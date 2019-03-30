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

fun main(args: Array<String>) {

    val config = Config {
        addSpec(ConfKey)
        addSpec(CryptocurrenciesKey)
        addSpec(GeoPosKey)
        addSpec(SleepKey)
    }.from.yaml.file("config.yml").from.env().from.systemProperties()

    val hardware = PhysicalHardware(config)

    Runtime.getRuntime().addShutdownHook(Thread {
        hardware.close() //todo maybe find cleaner way?
    })

    val database = DatabaseLayer(config[ConfKey.databaseFile])

    val frontDisplayViews = listOf(
            CalendarView(config, hardware),
            OutdoorWeatherView(database, hardware),
            GeigerView(database, hardware),
            CryptocurrencyView(GlobalScope.coroutineContext, config, database, hardware))

    val brightnessDaemon = BrightnessDaemon(config, database, hardware)

    val menus = listOf(
            BrightnessMenu(brightnessDaemon))

    val controllers = listOf(
            CpuUsageIndicatorDaemon(hardware),
            RealTimeClockDaemon(hardware),
            brightnessDaemon,
            FrontDisplayDaemon(GlobalScope.coroutineContext, hardware, frontDisplayViews, menus))

    runBlocking {
        controllers.map { GlobalScope.launch { it.startPooling() } }.joinAll()
    }
}
