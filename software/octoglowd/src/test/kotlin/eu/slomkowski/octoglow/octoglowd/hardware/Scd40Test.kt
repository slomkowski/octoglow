package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.I2CBuffer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.math.log
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@ExtendWith(HardwareParameterResolver::class)
class Scd40Test {

    private val logger = KotlinLogging.logger { }

    @Test
    fun `test readSerialNumber from mock`(): Unit = runBlocking {
        val hardwareMock = mockk<Hardware>()
        val scd40 = spyk(Scd40(hardwareMock))

        val mockedBuffer = intArrayOf(0x4c, 0x02, 0xde, 0xe3, 0x07, 0x4d, 0x3b, 0xe8, 0x51).toI2CBuffer()
        coEvery { scd40.doTransaction(any<I2CBuffer>(), any()) } returns mockedBuffer

        val result = scd40.readSerialNumber()
        assertThat(result).isEqualTo(0x4c02e3073be8)
    }

    @Test
    fun testInitAndRead(hardware: Hardware): Unit = runBlocking {

//        hardware.scd40.performSelfTest()

        repeat(20) {
            while (!hardware.scd40.getDataReadyStatus()) {
                delay(10000)
            }
            val report = hardware.scd40.readMeasurement()
            logger.info { "Read report: $report" }
        }
    }
}