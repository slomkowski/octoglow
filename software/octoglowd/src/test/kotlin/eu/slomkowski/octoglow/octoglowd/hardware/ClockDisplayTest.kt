package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToBitString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ExtendWith(HardwareParameterResolver::class)
class ClockDisplayTest {

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val DELTA = 0.05
    }

    @Test
    fun testCalculateChecksum() {
        val i2CBuffer = intArrayOf(0, 4, 6, 183, 160, 103, 51, 33) // first byte = 0 is dummy, this function doesn't check the whole message CRC
        logger.info { i2CBuffer.contentToBitString() }
        logger.info { "${RemoteSensorReport.toBitArray(i2CBuffer)}" }

        assertThat(RemoteSensorReport.calculateChecksum(i2CBuffer)).isTrue()
    }

    @Test
    fun testGetOutdoorWeatherReport(hardware: Hardware) {
        runBlocking {
            hardware.clockDisplay.apply {

                val report1 = retrieveRemoteSensorReport()
                    ?: fail("Report is invalid. Perhaps no measurement received yet?")

                logger.info { "Report 1: $report1" }
                val report2 = retrieveRemoteSensorReport()
                logger.info { "Report 2: $report2" }

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
            RemoteSensorReport.parse(intArrayOf(3))
        }

        assertNull(RemoteSensorReport.parse(intArrayOf(49, 4, 43, 34, 43, 43, 0, 0)))
    }

    @Test
    fun testGetOutdoorWeatherReportParse() {
        assertParsing(1, 23.9, 32.0, false, 102, 4, 6, 183, 160, 103, 51, 33)
        assertParsing(2, 28.22, 25.0, false, 54, 4, 2, 63, 209, 108, 2, 82)
        assertParsing(3, 26.44, 30.0, true, 188, 4, 6, 137, 164, 106, 3, 3)
        // todo negative temperatures in winter
    }

    private fun assertParsing(
        sensorId: Int,
        temperature: Double,
        humidity: Double,
        weakBattery: Boolean,
        vararg buffer: Int
    ) {
        val i2CBuffer = intArrayOf(*buffer)
        val report = assertNotNull(RemoteSensorReport.parse(i2CBuffer))

        assertThat(report.batteryIsWeak).isEqualTo(weakBattery)
        assertThat(report.sensorId).isEqualTo(sensorId)
        assertEquals(temperature, report.temperature, DELTA)
        assertEquals(humidity, report.humidity, DELTA)
    }

    @Test
    fun testSetBrightness(hardware: Hardware) {
        runBlocking {
            hardware.clockDisplay.apply {
                setDisplay(12, 34, upperDot = true, lowerDot = false)
                for (brightness in 0..5) {
                    setBrightness(brightness)
                    delay(600)
                }
            }
        }
    }

    @Test
    fun testSetRelay(hardware: Hardware): Unit = runBlocking {
        hardware.clockDisplay.apply {
            repeat(3) {
                setRelay(true)
                delay(100)
                setRelay(false)
                delay(50)
            }
        }
    }

    @Test
    fun testSetDisplay(hardware: Hardware): Unit = runBlocking {
        hardware.clockDisplay.apply {
            setDisplay(3, 58, upperDot = true, lowerDot = false)
            delay(1000)
            setDisplay(21, 2, upperDot = false, lowerDot = true)
            delay(1000)
            for (hour in 0..24) {
                for (minute in 0..59 step 3) {
                    setDisplay(hour, minute, upperDot = minute % 2 == 0, lowerDot = hour % 2 == 0)
                }
            }
        }
    }

    @Test
    fun testLightSensor(hardware: Hardware): Unit = runBlocking {
        repeat(30) {
            val lightSensorValue = hardware.clockDisplay.retrieveLightSensorMeasurement()
            logger.info { "Light sensor: $lightSensorValue." }
            delay(100.milliseconds)
            assertThat(lightSensorValue).isBetween(0, 1023)
        }
    }

    @Test
//    @Disabled("persistent displaying new reports")
    fun testConstantReportsReceiving(hardware: Hardware) {
        val reports = mutableListOf<RemoteSensorReport>()
        runBlocking {
            var lastReport: RemoteSensorReport? = null
            repeat(60 * 2) {
                val newReport = hardware.clockDisplay.retrieveRemoteSensorReport()
                if (newReport != null && newReport != lastReport) {
                    logger.info { "New report: $newReport" }
                    reports.add(newReport)
                }
                lastReport = newReport
                delay(1.seconds)
            }
        }
        assertThat(reports).isNotEmpty()
    }
}