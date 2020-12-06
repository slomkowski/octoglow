package eu.slomkowski.octoglow.octoglowd

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.*
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

val poznanCoordinates = 52.395869 to 16.929220

class UtilsTest {

    companion object : KLogging()

    @Test
    fun testTrySeveralTimes() {
        assertFails {
            runBlocking {
                trySeveralTimes<Unit>(5, logger) {
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

    @Test
    fun testFormatPressure() {
        assertEquals("1023 hPa", formatPressure(1022.99))
        assertEquals(" 994 hPa", formatPressure(994.2))
        assertEquals("---- hPa", formatPressure(null))
    }

    @Test
    fun testCalculate() {
        Duration.ofMinutes(5).let { min5 ->
            assertEquals(0, getSegmentNumber(Duration.ZERO, min5))
            assertEquals(0, getSegmentNumber(Duration.ofSeconds(3), min5))
            assertEquals(0, getSegmentNumber(Duration.ofSeconds(14), min5))
            assertEquals(0, getSegmentNumber(Duration.ofMillis(14_999), min5))

            assertEquals(1, getSegmentNumber(Duration.ofSeconds(15), min5))
            assertEquals(1, getSegmentNumber(Duration.ofSeconds(23), min5))

            assertEquals(0, getSegmentNumber(Duration.ofSeconds(1), min5))
            assertEquals(19, getSegmentNumber(Duration.ofSeconds(299), min5))
            assertEquals(19, getSegmentNumber(min5, min5))
        }
    }

    @Test
    fun testIsSleeping() {
        (LocalTime.of(22, 0) to Duration.ofHours(8)).let { (s, d) ->
            assertTrue { isSleeping(s, d, LocalTime.of(23, 0)) }
            assertFalse { isSleeping(s, d, LocalTime.of(12, 0)) }
            assertTrue { isSleeping(s, d, LocalTime.of(3, 0)) }
            assertFalse { isSleeping(s, d, LocalTime.of(21, 0)) }
        }

        (LocalTime.of(1, 20) to Duration.ofHours(7)).let { (s, d) ->
            assertFalse { isSleeping(s, d, LocalTime.of(23, 0)) }
            assertFalse { isSleeping(s, d, LocalTime.of(12, 0)) }
            assertTrue { isSleeping(s, d, LocalTime.of(3, 0)) }
            assertTrue { isSleeping(s, d, LocalTime.of(7, 0)) }
            assertFalse { isSleeping(s, d, LocalTime.of(21, 0)) }
        }
    }

    @Test
    fun testToLocalDateTimeInSystemTimeZone() {
        assertEquals(LocalDateTime.of(2019, 11, 20, 13, 23, 3),
                OffsetDateTime.of(2019, 11, 20, 16, 23, 3, 0, ZoneOffset.ofHours(4)).toLocalDateTimeInSystemTimeZone())
        assertEquals(LocalDateTime.of(2019, 7, 20, 14, 23, 3), OffsetDateTime.of(2019, 7, 20, 16, 23, 3, 0, ZoneOffset.ofHours(4)).toLocalDateTimeInSystemTimeZone())
    }
}