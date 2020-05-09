package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.daemon.BrightnessDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.CpuUsageIndicatorDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.RealTimeClockDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.*
import eu.slomkowski.octoglow.octoglowd.hardware.PhysicalHardware
import kotlinx.coroutines.*

fun main() {

    val config = Config {
        addSpec(ConfKey)
        addSpec(CryptocurrenciesKey)
        addSpec(GeoPosKey)
        addSpec(SleepKey)
        addSpec(NetworkViewKey)
        addSpec(NbpKey)
        addSpec(SimpleMonitorKey)
    }.from.yaml.file("config.yml")

    val hardware = PhysicalHardware(config)

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
