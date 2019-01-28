package eu.slomkowski.octoglow.octoglowd.hardware

import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.newSingleThreadContext

class Hardware(i2cBusNumber: Int) : HasBrightness, AutoCloseable {

    private val threadContext = newSingleThreadContext("hardware")

    private val bus = I2CBus(i2cBusNumber)

    val clockDisplay = ClockDisplay(threadContext, bus)

    val frontDisplay = FrontDisplay(threadContext, bus)

    val geiger = Geiger(threadContext, bus)

    val dac = Dac(threadContext, bus)

    override suspend fun setBrightness(brightness: Int) {
        listOf<HasBrightness>(clockDisplay, frontDisplay, geiger).forEach { it.setBrightness(brightness) }
    }

    override fun close() {
        listOf<AutoCloseable>(clockDisplay, frontDisplay, geiger, dac).forEach { it.close() }
    }
}