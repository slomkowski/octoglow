package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.daemon.BrightnessDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.CpuUsageIndicatorDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.RealTimeClockDaemon
import eu.slomkowski.octoglow.octoglowd.daemon.view.CalendarView
import eu.slomkowski.octoglow.octoglowd.daemon.view.CryptocurrencyView
import eu.slomkowski.octoglow.octoglowd.daemon.view.GeigerView
import eu.slomkowski.octoglow.octoglowd.daemon.view.OutdoorWeatherView
import eu.slomkowski.octoglow.octoglowd.hardware.PhysicalHardware
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

    val hardware = PhysicalHardware(config[ConfKey.i2cBus])

    Runtime.getRuntime().addShutdownHook(Thread {
        hardware.close() //todo maybe find cleaner way?
    })

    val database = DatabaseLayer(config[ConfKey.databaseFile])

    val frontDisplayViews = listOf(
            CalendarView(config, hardware),
            OutdoorWeatherView(database, hardware),
            GeigerView(database, hardware),
            CryptocurrencyView(config, database, hardware))

    val controllers = listOf(
            CpuUsageIndicatorDaemon(hardware),
            RealTimeClockDaemon(hardware),
            BrightnessDaemon(config, hardware),
            FrontDisplayDaemon(hardware, frontDisplayViews))

    runBlocking {
        controllers.map { launch { it.startPooling() } }.joinAll()
    }
}
