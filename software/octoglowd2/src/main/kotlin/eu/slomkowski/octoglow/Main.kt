package eu.slomkowski.octoglow

import eu.slomkowski.octoglow.hardware.ClockDisplay
import eu.slomkowski.octoglow.hardware.FrontDisplay
import eu.slomkowski.octoglow.view.AboutView
import eu.slomkowski.octoglow.view.OutdoorWeatherView
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths


fun main(args: Array<String>) {
    println("Hello, World")

    val bus = I2CBus("/dev/i2c-4")
    val clockDisplay = ClockDisplay(bus)
    val frontDisplay = FrontDisplay(bus)

    val database = DatabaseLayer(Paths.get("data.db"))

    val frontDisplayViews = listOf(
            AboutView(frontDisplay),
            OutdoorWeatherView(database, frontDisplay, clockDisplay))

    runBlocking {

        joinAll(createRealTimeClockController(clockDisplay),
                createFrontDisplayController(frontDisplay, frontDisplayViews))
    }
}

