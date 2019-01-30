package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
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

    val hardware = Hardware(config[ConfKey.i2cBus])

    Runtime.getRuntime().addShutdownHook(Thread {
        hardware.close() //todo maybe find cleaner way?
    })

    val database = DatabaseLayer(config[ConfKey.databaseFile])

    val frontDisplayViews = listOf(
            AboutView(hardware),
            OutdoorWeatherView(database, hardware),
            CryptocurrencyView(config, database, hardware))

    val controllers = listOf(
            BrightnessController(config, hardware))

    runBlocking {

        listOf(
                createRealTimeClockController(hardware.clockDisplay),
                createCpuUsageIndicatorController(hardware.dac),
                createFrontDisplayController(hardware.frontDisplay, frontDisplayViews))
                .plus(controllers.map { it.startPooling() }).joinAll()
    }
}

