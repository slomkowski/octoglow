package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToBitString
import io.dvlopt.linux.i2c.I2CBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import kotlin.test.*

@ExtendWith(HardwareParameterResolver::class)
class ClockDisplayTest {

    companion object : KLogging() {
        private const val DELTA = 0.05
    }

    @Test
    fun testCalculateChecksum() {
        val i2CBuffer = createI2CBuffer(4, 6, 183, 160, 103, 51, 33)
        logger.info { i2CBuffer.contentToBitString() }
        logger.info("{}", OutdoorWeatherReport.toBitArray(i2CBuffer))

        assertTrue(OutdoorWeatherReport.calculateChecksum(i2CBuffer))
    }

    @Test
    fun testGetOutdoorWeatherReport(hardware: Hardware) {
        runBlocking {
            ClockDisplay(hardware).apply {
                initDevice()

                val report1 = getOutdoorWeatherReport()
                    ?: fail("Report is invalid. Perhaps no measurement received yet?")

                logger.info("Report 1: {}", report1)
                val report2 = getOutdoorWeatherReport()
                logger.info("Report 2: {}", report2)

                assertNotNull(report1)
                assertNotNull(report2)

                assertTrue(report2.alreadyReadFlag)

                assertEquals(report1.temperature, report2.temperature, DELTA)
                assertEquals(report1.humidity, report2.humidity, DELTA)
                assertEquals(report1.batteryIsWeak, report2.batteryIsWeak)
            }
        }
    }

    @Test
    fun testParseInvalid() {
        assertFails {
            OutdoorWeatherReport.parse(I2CBuffer(3))
        }

        assertFailsWith(IllegalStateException::class) {
            OutdoorWeatherReport.parse(I2CBuffer(5).set(0, 4).set(1, 43).set(2, 34).set(3, 43).set(4, 43))
        }
    }

    @Test
    fun testGetOutdoorWeatherReportParse() {
        assertParsing(23.9, 32.0, 4, 6, 183, 160, 103, 51, 33)

        //todo more positive temp
    }

    private fun createI2CBuffer(vararg buffer: Int) = I2CBuffer(buffer.size).apply {
        require(buffer.size == 7)
        buffer.forEachIndexed { i, v -> this.set(i, v) }
    }

    private fun assertParsing(temperature: Double, humidity: Double, vararg buffer: Int) {
        val i2CBuffer = createI2CBuffer(*buffer)
        val report = assertNotNull(OutdoorWeatherReport.parse(i2CBuffer))

        assertEquals(temperature, report.temperature, DELTA)
        assertEquals(humidity, report.humidity, DELTA)
    }

    @Test
    fun testSetBrightness(hardware: Hardware) {
        runBlocking {
            ClockDisplay(hardware).apply {
                setDisplay(12, 34, true, false)
                for (brightness in 0..5) {
                    setBrightness(brightness)
                    delay(600)
                }
            }
        }
    }

    @Test
    fun testSetDisplay(hardware: Hardware) {
        runBlocking {
            ClockDisplay(hardware).apply {
                setDisplay(3, 58, true, false)
                delay(1000)
                setDisplay(21, 2, false, true)
            }
        }
    }

    @Test
    @Disabled("ringing is scary, so only done when explicitly needed")
    fun testRingBell(hardware: Hardware) {
        runBlocking {
            ClockDisplay(hardware).apply {
                ringBell(Duration.ofMillis(100))
                delay(1000)
                ringBell(Duration.ofMillis(500))
            }
        }
    }

    @Test
    @Disabled("persistent displaying new reports")
    fun testConstantReportsReceiving(hardware: Hardware) {
        runBlocking {
            ClockDisplay(hardware).apply {

                repeat(100000) {
                    getOutdoorWeatherReport()
                    delay(300)
                }

            }
        }
    }
}