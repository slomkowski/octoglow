package eu.slomkowski.octoglow.octoglowd.hardware

import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.newSingleThreadContext

interface Hardware : HasBrightness, AutoCloseable {
    val clockDisplay: ClockDisplay
    val frontDisplay: FrontDisplay
    val geiger: Geiger
    val dac: Dac

    override suspend fun setBrightness(brightness: Int)

    override fun close()
}

class PhysicalHardware(i2cBusNumber: Int) : Hardware {

    private val threadContext = newSingleThreadContext("hardware")

    private val bus = I2CBus(i2cBusNumber)

    override val clockDisplay = ClockDisplay(threadContext, bus)

    override val frontDisplay = FrontDisplay(threadContext, bus)

    override val geiger = Geiger(threadContext, bus)

    override val dac = Dac(threadContext, bus)

    override suspend fun setBrightness(brightness: Int) {
        listOf<HasBrightness>(clockDisplay, frontDisplay, geiger).forEach { it.setBrightness(brightness) }
    }

    override fun close() {
        listOf<AutoCloseable>(clockDisplay, frontDisplay, geiger, dac).forEach { it.close() }
    }
}