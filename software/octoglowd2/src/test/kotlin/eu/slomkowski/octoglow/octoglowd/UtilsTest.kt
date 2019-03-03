package eu.slomkowski.octoglow.octoglowd

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFails

class UtilsTest {

    companion object : KLogging()

    @Test
    fun testTrySeveralTimes() {
        assertFails {
            runBlocking {
                trySeveralTimes(5, logger) {
                    throw IllegalStateException("always fail")
                }
            }
        }

        runBlocking {
            trySeveralTimes(5, logger) {
                logger.info { "always run OK" }
            }
        }

        runBlocking {
            trySeveralTimes(5, logger) { tryNumber ->
                if (tryNumber < 5) {
                    throw IllegalStateException("only last attempt doesn't fail")
                }
            }
        }
    }

    @Test
    fun testFormatHumidity() {
        assertEquals("H:100%", formatHumidity(100.0))
        assertEquals("H: 3%", formatHumidity(3.0))
        assertEquals("H: 0%", formatHumidity(0.0))
        assertEquals("H:24%", formatHumidity(24.1001))
        assertEquals("H:39%", formatHumidity(38.8903))
        assertEquals("H:--%", formatHumidity(null))
    }

    @Test
    fun testFormatTemperature() {
        assertEquals("+24.4\u00B0C", formatTemperature(24.434))
        assertEquals("-12.0\u00B0C", formatTemperature(-12.0))
        assertEquals(" +3.2\u00B0C", formatTemperature(3.21343))
        assertEquals("-12.7\u00B0C", formatTemperature(-12.693423))
        assertEquals(" +0.0\u00B0C", formatTemperature(0.0))
        assertEquals("---.-\u00B0C", formatTemperature(null))
    }
}