package eu.slomkowski.octoglow.octoglowd

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformValues
import eu.slomkowski.octoglow.octoglowd.dataharvesters.*
import eu.slomkowski.octoglow.octoglowd.demon.*
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.*
import eu.slomkowski.octoglow.octoglowd.firmware.FirmwareBurner
import eu.slomkowski.octoglow.octoglowd.firmware.FirmwareTarget
import eu.slomkowski.octoglow.octoglowd.firmware.IntelHex
import eu.slomkowski.octoglow.octoglowd.hardware.FrontDisplay
import eu.slomkowski.octoglow.octoglowd.hardware.HardwareReal
import eu.slomkowski.octoglow.octoglowd.mqtt.MqttDemon
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.random.Random
import kotlin.time.ExperimentalTime

data class BurnFirmwareRequest(
    val target: FirmwareTarget,
    val hexFile: Path,
)

class OctoglowCommand : CoreCliktCommand(name = "octoglowd") {

    init {
        // clikt-core doesn't exit the process by default, only the full clikt does
        context { exitProcess = { status -> kotlin.system.exitProcess(status) } }
    }

    private val burnFirmware: BurnFirmwareRequest? by option(
        "--burn-firmware",
        metavar = "DEVICE HEX_FILE",
    ).transformValues(2) { (deviceName, fileName) ->
        val target = FirmwareTarget.fromCommandLineName(deviceName)
            ?: fail("unknown device '$deviceName', supported: ${FirmwareTarget.entries.joinToString { it.commandLineName }}")
        val hexFile = Paths.get(fileName)
        if (!hexFile.isRegularFile()) {
            fail("file '$fileName' doesn't exist")
        }
        BurnFirmwareRequest(target, hexFile)
    }.help(
        "Upload the firmware over I2C instead of running the daemon. Stop the running daemon first. " +
                "Supported devices: ${FirmwareTarget.entries.joinToString { it.commandLineName }}."
    )

    override fun help(context: Context) = "Octoglow VFD display daemon. Without options, runs the daemon."

    override fun run() {
        when (val request = burnFirmware) {
            null -> runDaemon()
            // also for success: the I2C bus thread would keep the JVM running
            else -> throw ProgramResult(burnFirmware(request.target, request.hexFile))
        }
    }
}

fun main(args: Array<String>) = OctoglowCommand().main(args)

@OptIn(ExperimentalTime::class)
private fun burnFirmware(target: FirmwareTarget, hexFile: Path): Int {
    val logger = KotlinLogging.logger {}

    val image = try {
        IntelHex.parse(hexFile)
    } catch (e: Exception) {
        logger.error { "Cannot read firmware from $hexFile: ${e.message}" }
        return 1
    }

    logger.info { "Burning $hexFile (${image.endAddress} bytes) to $target..." }

    val config = Config.parse(Paths.get("config.json"))
    // not closed: closing the devices would replace the result on the displays, the process exits anyway
    val hardware = HardwareReal(config)

    // the front display can't show the progress of its own update, only the result
    val showProgress = target != FirmwareTarget.FRONT_DISPLAY

    return try {
        runBlocking {
            if (showProgress) {
                hardware.frontDisplay.showFirmwareProgress(target, "connecting")
            }

            FirmwareBurner(hardware, target, onProgress = { written, total ->
                if (showProgress) {
                    hardware.frontDisplay.showFirmwareProgress(target, "${100 * written / total}%")
                }
            }).burn(image)

            hardware.frontDisplay.showFirmwareProgress(target, "done")
        }
        logger.info { "Firmware of $target updated." }
        0
    } catch (e: Exception) {
        logger.error(e) { "Firmware update of $target failed." }
        runBlocking { hardware.frontDisplay.showFirmwareProgress(target, "FAILED") }
        1
    }
}

/**
 * Both lines are padded to the display width, so no clearing (and flicker) is needed.
 * The progress is only informational, so display errors are ignored.
 */
private suspend fun FrontDisplay.showFirmwareProgress(target: FirmwareTarget, status: String) {
    fun line(text: String) = text.take(20).padEnd(20)
    try {
        setStaticText(0, line("FW: ${target.commandLineName}"))
        setStaticText(20, line(status))
    } catch (_: Exception) {
    }
}

@OptIn(ExperimentalTime::class)
private fun runDaemon() {
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Octoglow daemon..." }

    val config = Config.parse(Paths.get("config.json"))

    val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val commandBus = CommandBus()
    val eventBus = DataSnapshotBus()

    val mqttDemon = MqttDemon(config, eventBus, commandBus)
    val hardware = HardwareReal(config)
    val database = DatabaseDemon(config.databaseFile, eventBus, config.historicalValuesRetention)

    val frontDisplayViews2 = listOf(
        CalendarView(config, hardware),
        ShoppingSundayView(config, hardware),

        IndoorWeatherView(config, database, hardware),
        OutdoorWeatherView(config, database, hardware),
        AirQualityView(config, database, hardware),

        GeigerView(database, hardware),

        CryptocurrencyView(config, database, hardware),
        NbpView(config, hardware),

        SimpleMonitorView(hardware),
        TodoistView(hardware),
        PoznanGarbageCollectionTimetableView(hardware),

        NetworkView(hardware),
        JvmMemoryView(hardware),
    )

    val brightnessDaemon = BrightnessDemon(config, database, hardware)

    val magicEyeMenu = MagicEyeMenu(eventBus, commandBus)
    val backlightMenu = BacklightMenu(eventBus, commandBus)


    val menus = listOf(
        BrightnessMenu(brightnessDaemon),
        magicEyeMenu,
        backlightMenu,
    )

    val realTimeClockDemon = RealTimeClockDemon(hardware)

    val demons = listOf(
        database,
        realTimeClockDemon,
        FrontDisplayDemon(config, workerScope, hardware, frontDisplayViews2, menus, eventBus, commandBus, realTimeClockDemon),
        AnalogGaugeDemon(hardware),
        brightnessDaemon,
        RadmonOrgSenderDemon(config, eventBus),
        MagicEyeDemon(hardware, eventBus, commandBus),
        BacklightDemon(hardware, eventBus, commandBus),
        magicEyeMenu,
        backlightMenu,
        mqttDemon,

        AirQualityDataHarvester(config, eventBus),
        CryptocurrencyDataHarvester(config, eventBus),
        GeigerDataHarvester(hardware, eventBus),
        LocalSensorsDataHarvester(config, hardware, eventBus),
        NbpDataHarvester(config, eventBus),
        RadioWeatherSensorDataHarvester(config, hardware, eventBus),
        SimplemonitorDataHarvester(config, eventBus),
        TodoistDataHarvester(config, eventBus),
        NetworkDataHarvester(config, eventBus),
        PoznanGarbageCollectionTimetableDataHarvester(config, eventBus),
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Shutting down. Calling workers to stop." }
        demons.forEach { it.close(workerScope) }
        Thread.sleep(2000)
        workerScope.cancel()
        runBlocking {
            workerScope.coroutineContext[Job]?.join()
        }
        hardware.close()
        logger.info { "Shut down." }
    })

    runBlocking {
        demons.forEach {
            launch {
                delay(Random.nextLong(1500, 4000))
                it.createJobs(workerScope)
            }
        }
        awaitCancellation()
    }
}
