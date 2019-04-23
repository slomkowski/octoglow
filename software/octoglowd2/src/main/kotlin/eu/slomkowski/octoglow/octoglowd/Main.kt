package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.daemon.BrightnessDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.CpuUsageIndicatorDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.RealTimeClockDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.*
import eu.slomkowski.octoglow.octoglowd.hardware.PhysicalHardware
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {

    val config = Config {
        addSpec(ConfKey)
        addSpec(CryptocurrenciesKey)
        addSpec(GeoPosKey)
        addSpec(SleepKey)
        addSpec(NetworkViewKey)
        addSpec(NbpKey)
    }.from.yaml.file("config.yml").from.env().from.systemProperties()

    val hardware = PhysicalHardware(config)

    Runtime.getRuntime().addShutdownHook(Thread {
        hardware.close() //todo maybe find cleaner way?
    })

    val database = DatabaseLayer(config[ConfKey.databaseFile])

    val frontDisplayViews = listOf(
            CalendarView(config, hardware),
            IndoorWeatherView(config, database, hardware),
            OutdoorWeatherView(database, hardware),
            GeigerView(database, hardware),
            CryptocurrencyView(config, database, hardware),
            NbpView(config, hardware),
            NetworkView(config, hardware))

    val brightnessDaemon = BrightnessDaemon(config, database, hardware)

    val menus = listOf(
            BrightnessMenu(brightnessDaemon))

    val controllers = listOf(
            CpuUsageIndicatorDaemon(hardware),
            RealTimeClockDaemon(hardware),
            brightnessDaemon,
            FrontDisplayDaemon(config, GlobalScope.coroutineContext, hardware, frontDisplayViews, menus))

    //todo add proper exception handling, with optional ringing
    runBlocking {
        controllers.map { GlobalScope.launch { it.startPooling() } }.joinAll()
    }
}
