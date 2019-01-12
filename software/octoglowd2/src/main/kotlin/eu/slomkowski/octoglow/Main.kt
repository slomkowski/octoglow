package eu.slomkowski.octoglow

import eu.slomkowski.octoglow.hardware.ClockDisplay
import eu.slomkowski.octoglow.hardware.FrontDisplay
import io.dvlopt.linux.i2c.I2CBus
import io.dvlopt.linux.i2c.I2CFunctionality
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ticker
import java.time.LocalDateTime


fun main(args: Array<String>) {
    println("Hello, World")

    val bus = I2CBus("/dev/i2c-0")
    val clockDisplay = ClockDisplay(bus)
    val frontDisplay = FrontDisplay(bus)

    println("Transactions: " + bus.functionalities.can(I2CFunctionality.TRANSACTIONS))

    runBlocking {
        joinAll(createRealTimeClockController(clockDisplay),
        createFrontDisplayController(frontDisplay))
    }
}

