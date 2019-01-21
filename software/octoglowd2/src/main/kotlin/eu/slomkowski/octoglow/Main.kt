package eu.slomkowski.octoglow

import eu.slomkowski.octoglow.hardware.ClockDisplay
import eu.slomkowski.octoglow.hardware.FrontDisplay
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths


fun main(args: Array<String>) {
    println("Hello, World")

    val bus = I2CBus("/dev/i2c-0")
    val clockDisplay = ClockDisplay(bus)
    val frontDisplay = FrontDisplay(bus)

    runBlocking {
        val database = DatabaseLayer(Paths.get("data.db"))

        joinAll(createRealTimeClockController(clockDisplay),
                createFrontDisplayController(frontDisplay))
    }
}

