package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.dataharvesters.*
import eu.slomkowski.octoglow.octoglowd.demon.*
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.*
import eu.slomkowski.octoglow.octoglowd.hardware.HardwareReal
import eu.slomkowski.octoglow.octoglowd.mqtt.MqttDemon
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.nio.file.Paths
import kotlin.random.Random
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun main() {
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Octoglow daemon..." }

    val config = Config.parse(Paths.get("config.json"))

    val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val commandBus = CommandBus()
    val eventBus = DataSnapshotBus()

    val mqttDemon = MqttDemon(config, eventBus, commandBus)
    val hardware = HardwareReal(config)
    val database = DatabaseDemon(config.databaseFile, eventBus)

    val frontDisplayViews2 = listOf(
        CalendarView(config, hardware),

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

    val menus = listOf(
        BrightnessMenu(brightnessDaemon),
        magicEyeMenu,
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
        magicEyeMenu,
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
