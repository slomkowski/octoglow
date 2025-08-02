package eu.slomkowski.octoglow.octoglowd.hardware.mock

import eu.slomkowski.octoglow.octoglowd.hardware.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

class HardwareMock : Hardware {
    private val logger = KotlinLogging.logger { }

    override val clockDisplay: ClockDisplay = mockk()

    override val frontDisplay: FrontDisplayMock = FrontDisplayMock()
    override val geiger = mockk<Geiger>(relaxed = true)
    override val dac = mockk<Dac>(relaxed = true)
    override val scd40 = mockk<Scd40>(relaxed = true)
    override val bme280 = mockk<Bme280>(relaxed = true)

    private val _brightness: AtomicInteger = AtomicInteger(3)

    val brightness: Int
        get() = _brightness.get()

    override fun close() {
        logger.info { "Closing hardware mock." }
    }

    override suspend fun setBrightness(brightness: Int) {
        logger.info { "Setting brightness to $brightness." }
        this._brightness.set(brightness)
    }

    override suspend fun doWrite(i2cAddress: Int, writeData: IntArray) {
        TODO("Not yet implemented")
    }

    override suspend fun doTransaction(i2cAddress: Int, writeData: IntArray, bytesToRead: Int, delayBetweenWriteAndRead: Duration): IntArray {
        TODO("Not yet implemented")
    }
}