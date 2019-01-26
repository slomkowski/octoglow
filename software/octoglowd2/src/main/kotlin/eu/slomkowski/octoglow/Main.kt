package eu.slomkowski.octoglow

import eu.slomkowski.octoglow.hardware.Hardware
import eu.slomkowski.octoglow.view.AboutView
import eu.slomkowski.octoglow.view.OutdoorWeatherView
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths


fun main(args: Array<String>) {

    val hardware = Hardware(Paths.get("/dev/i2c-4"))
    val database = DatabaseLayer(Paths.get("data.db"))

    val frontDisplayViews = listOf(
            AboutView(hardware),
            OutdoorWeatherView(database, hardware))

    runBlocking {
        joinAll(createRealTimeClockController(hardware.clockDisplay),
                createFrontDisplayController(hardware.frontDisplay, frontDisplayViews))
    }
}

