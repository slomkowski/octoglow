package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.daemon.AnalogGaugeDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.BrightnessDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.RealTimeClockDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.nio.file.Paths
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun main() {
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Octoglow daemon..." }

    val config = Config.parse(Paths.get("config.json"))

    val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val hardware = Hardware(config)
    val database = DatabaseLayer(config.databaseFile)
    val closeable = listOf(database, hardware)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Shutting down. Calling workers to stop." }
        workerScope.cancel()
        runBlocking {
            workerScope.coroutineContext[Job]?.join()
        }
        closeable.forEach { it.close() }
        logger.info { "Shut down." }
    })

    val frontDisplayViews = listOf(
        CalendarView(config, hardware),
        WeatherSensorView(config, database, hardware),
        GeigerView(database, hardware),
        CryptocurrencyView(config, database, hardware),
        NbpView(config, hardware),
        SimpleMonitorView(config, hardware),
        AirQualityView(config, database, hardware),
        NetworkView(config, hardware),
        JvmMemoryView(hardware),
    )

    val brightnessDaemon = BrightnessDaemon(config, database, hardware)

    val menus = listOf(
        BrightnessMenu(brightnessDaemon)
    )

    val demons = listOf(
        AnalogGaugeDaemon(hardware),
        RealTimeClockDaemon(hardware),
        brightnessDaemon,
        FrontDisplayDaemon(config, workerScope, hardware, frontDisplayViews, menus)
    )

    runBlocking {
        demons.forEach { workerScope.launch { it.createJob() } }

        awaitCancellation()
    }
}
