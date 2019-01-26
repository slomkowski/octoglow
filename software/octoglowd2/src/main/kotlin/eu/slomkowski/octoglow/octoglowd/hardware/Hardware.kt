package eu.slomkowski.octoglow.octoglowd.hardware

import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.newSingleThreadContext
import java.nio.file.Path

class Hardware(i2cBusPath: Path) : HasBrightness {
    private val threadContext = newSingleThreadContext("hardware")

    private val bus = I2CBus(i2cBusPath.toString())

    val clockDisplay = ClockDisplay(threadContext, bus)

    val frontDisplay = FrontDisplay(threadContext, bus)

    val geiger = Geiger(threadContext, bus)

    override suspend fun setBrightness(brightness: Int) {
        listOf<HasBrightness>(clockDisplay, frontDisplay, geiger).forEach { it.setBrightness(brightness) }
    }
}