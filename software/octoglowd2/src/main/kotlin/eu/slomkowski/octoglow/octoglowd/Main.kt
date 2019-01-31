package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.controller.BrightnessController
import eu.slomkowski.octoglow.octoglowd.controller.CpuUsageIndicatorController
import eu.slomkowski.octoglow.octoglowd.controller.FrontDisplayController
import eu.slomkowski.octoglow.octoglowd.controller.RealTimeClockController
import eu.slomkowski.octoglow.octoglowd.hardware.PhysicalHardware
import eu.slomkowski.octoglow.octoglowd.view.AboutView
import eu.slomkowski.octoglow.octoglowd.view.CryptocurrencyView
import eu.slomkowski.octoglow.octoglowd.view.OutdoorWeatherView
import kotlinx.coroutines.joinAll
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
            AboutView(hardware),
            OutdoorWeatherView(database, hardware),
            CryptocurrencyView(config, database, hardware))

    val controllers = listOf(
            CpuUsageIndicatorController(hardware),
            FrontDisplayController(hardware, frontDisplayViews),
            RealTimeClockController(hardware),
            BrightnessController(config, hardware))

    runBlocking {
        controllers.map { it.startPooling() }.joinAll()
    }
}

