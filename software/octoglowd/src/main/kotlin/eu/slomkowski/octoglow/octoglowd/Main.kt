package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.daemon.AnalogGaugeDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.BrightnessDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.RealTimeClockDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

fun main() {
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Octoglow daemon..." }

    val config = Config.parse(Paths.get("config.json"))

    val hardware = Hardware(config)

    Runtime.getRuntime().addShutdownHook(Thread {
        hardware.close() //todo maybe find cleaner way?
    })

    val database = DatabaseLayer(config.databaseFile, CoroutineExceptionHandler { context, throwable ->
        runBlocking {
            handleException(config, DatabaseLayer.logger, hardware, context, throwable)
        }
    })

    val frontDisplayViews = listOf(
        CalendarView(config, hardware),
        WeatherSensorView(config, database, hardware),
        GeigerView(database, hardware),
        CryptocurrencyView(config, database, hardware),
        NbpView(config, hardware),
//            StockView(config, database, hardware),
        SimpleMonitorView(config, database, hardware),
        AirQualityView(config, database, hardware),
        NetworkView(config, hardware)
    )

    val brightnessDaemon = BrightnessDaemon(config, database, hardware)

    val menus = listOf(
        BrightnessMenu(brightnessDaemon)
    )

    val controllers = listOf(
        AnalogGaugeDaemon(config, hardware),
        RealTimeClockDaemon(config, hardware),
        brightnessDaemon,
        FrontDisplayDaemon(config, GlobalScope.coroutineContext, hardware, frontDisplayViews, menus)
    )

    runBlocking {
        controllers.forEach { launch { it.createJob() } }
    }
}
