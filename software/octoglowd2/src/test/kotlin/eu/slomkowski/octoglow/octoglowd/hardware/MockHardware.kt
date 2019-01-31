package eu.slomkowski.octoglow.octoglowd.hardware

import io.mockk.mockk
import mu.KLogging

class MockHardware : Hardware {

    companion object : KLogging()

    override val clockDisplay: ClockDisplay
        get() = mockk()
    override val frontDisplay: FrontDisplay
        get() = mockk()
    override val geiger: Geiger
        get() = mockk()
    override val dac: Dac
        get() = mockk()

    override suspend fun setBrightness(brightness: Int) {
        logger.info { "Brightness for all devices set to $brightness." }
    }

    override fun close() {
        logger.info { "close() function called." }
    }
}