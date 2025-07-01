package eu.slomkowski.octoglow.octoglowd.hardware.mock

import eu.slomkowski.octoglow.octoglowd.hardware.*
import io.dvlopt.linux.i2c.I2CBuffer
import io.mockk.mockk
import kotlin.time.Duration

class HardwareMock : Hardware {
    override val clockDisplay: ClockDisplay = mockk()

    override val frontDisplay: FrontDisplayMock = FrontDisplayMock()
    override val geiger = mockk<Geiger>(relaxed = true)
    override val dac = mockk<Dac>(relaxed = true)
    override val scd40 = mockk<Scd40>(relaxed = true)
    override val bme280 = mockk<Bme280>(relaxed = true)

    override fun close() {}

    override suspend fun setBrightness(brightness: Int) {}

    override suspend fun doWrite(i2cAddress: Int, writeBuffer: I2CBuffer) {
        TODO("Not yet implemented")
    }

    override suspend fun doTransaction(
        i2cAddress: Int,
        writeBuffer: I2CBuffer,
        bytesToRead: Int,
        delayBetweenWriteAndRead: Duration
    ): I2CBuffer {
        TODO("not implemented")
    }
}