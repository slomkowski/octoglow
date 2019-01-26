package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToString
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(I2CBusParameterResolver::class)
class ClockDisplayTest {

    companion object : KLogging()

    @Test
    fun testGetOutdoorWeatherReport(i2CBus: I2CBus) {
        runBlocking {
            ClockDisplay(coroutineContext, i2CBus).apply {
                val report1 = getOutdoorWeatherReport()
                logger.info("Report 1: {}", report1)
                val report2 = getOutdoorWeatherReport()
                logger.info("Report 2: {}", report2)

                assertTrue(report2.alreadyReadFlag)

                assertEquals(report1.temperature, report2.temperature)
                assertEquals(report1.humidity, report2.humidity)
                assertEquals(report1.batteryIsWeak, report2.batteryIsWeak)
            }
        }
    }

    @Test
    fun testParseOutdoorWeatherReport() {
        // -1.8 stopnia 72% wilg : 4 238 15 72 1 0
        // -2.6 stopnia 71% wilg : 4 230 15 71 1 1
        val b1 = I2CBuffer(6).set(0, 4).set(1, 238).set(2, 15).set(3, 72).set(4, 1).set(5, 0)
        assertEquals("[4 238 15 72 1 0]", b1.contentToString())

        OutdoorWeatherReport.parse(b1).apply {
            assertEquals(-1.8, temperature)
            assertEquals(72.0, humidity)
        }

        OutdoorWeatherReport.parse(I2CBuffer(6).set(0, 4).set(1, 230).set(2, 15).set(3, 71).set(4, 1).set(5, 1)).apply {
            assertEquals(-2.6, temperature)
            assertEquals(71.0, humidity)
            assertTrue(alreadyReadFlag)
            assertTrue(batteryIsWeak)
        }
    }

    @Test
    fun testSetBrightness(i2CBus: I2CBus) {
        runBlocking {
            ClockDisplay(coroutineContext, i2CBus).apply {
                for (brightness in 0..5) {
                    setBrightness(brightness)
                    delay(600)
                }
            }
        }
    }

    @Test
    fun testSetDisplay(i2CBus: I2CBus) {
        runBlocking {
            ClockDisplay(coroutineContext, i2CBus).apply {
                setDisplay(3, 58, true, false)
                delay(1000)
                setDisplay(21, 2, false, true)
            }
        }
    }
}