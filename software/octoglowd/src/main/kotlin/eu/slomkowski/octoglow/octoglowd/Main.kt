package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.daemon.BrightnessDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.CpuUsageIndicatorDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.RealTimeClockDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.*

fun main() {

    val config = Config {
        addSpec(ConfKey)
        addSpec(CryptocurrenciesKey)
        addSpec(GeoPosKey)
        addSpec(SleepKey)
        addSpec(NetworkViewKey)
        addSpec(NbpKey)
        addSpec(StocksKey)
        addSpec(SimpleMonitorKey)
    }.from.yaml.file("config.yml")

    val hardware = Hardware(config)

    Runtime.getRuntime().addShutdownHook(Thread {
        hardware.close() //todo maybe find cleaner way?
    })

    val database = DatabaseLayer(config[ConfKey.databaseFile], CoroutineExceptionHandler { context, throwable ->
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
            StockView(config, database, hardware),
            SimpleMonitorView(config, database, hardware),
            NetworkView(config, hardware))

    val brightnessDaemon = BrightnessDaemon(config, database, hardware)

    val menus = listOf(
            BrightnessMenu(brightnessDaemon))

    val controllers = listOf(
            CpuUsageIndicatorDaemon(config, hardware),
            RealTimeClockDaemon(config, hardware),
            brightnessDaemon,
            FrontDisplayDaemon(config, GlobalScope.coroutineContext, hardware, frontDisplayViews, menus))

    runBlocking {
        controllers.map { GlobalScope.launch { it.startPooling() } }.joinAll()
    }
}
