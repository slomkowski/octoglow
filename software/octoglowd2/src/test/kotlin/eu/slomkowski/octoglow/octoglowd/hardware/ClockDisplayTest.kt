package eu.slomkowski.octoglow.octoglowd.hardware

import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@ExtendWith(I2CBusParameterResolver::class)
class ClockDisplayTest {

    companion object : KLogging()

    @Test
    fun testGetOutdoorWeatherReport(i2CBus: I2CBus) {
        runBlocking {
            ClockDisplay(coroutineContext, i2CBus).apply {
                val report1 = getOutdoorWeatherReport()
                        ?: fail("Report is invalid. Perhaps no measurement received yet?")

                logger.info("Report 1: {}", report1)
                val report2 = getOutdoorWeatherReport()
                logger.info("Report 2: {}", report2)

                assertNotNull(report1)
                assertNotNull(report2)

                assertTrue(report2.alreadyReadFlag)

                assertEquals(report1.temperature, report2.temperature)
                assertEquals(report1.humidity, report2.humidity)
                assertEquals(report1.batteryIsWeak, report2.batteryIsWeak)
            }
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