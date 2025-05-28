package eu.slomkowski.octoglow.octoglowd

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertFails
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val poznanCoordinates = 52.395869 to 16.929220

class UtilsTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

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
        assertEquals("100%", formatHumidity(100.0))
        assertEquals(" 3%", formatHumidity(3.0))
        assertEquals(" 0%", formatHumidity(0.0))
        assertEquals("24%", formatHumidity(24.1001))
        assertEquals("39%", formatHumidity(38.8903))
        assertEquals("--%", formatHumidity(null))
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
        5.minutes.let { min5 ->
            assertEquals(0, getSegmentNumber(Duration.ZERO, min5))
            assertEquals(0, getSegmentNumber(3.seconds, min5))
            assertEquals(0, getSegmentNumber(14.seconds, min5))
            assertEquals(0, getSegmentNumber(14_999.milliseconds, min5))

            assertEquals(1, getSegmentNumber(15.seconds, min5))
            assertEquals(1, getSegmentNumber(23.seconds, min5))

            assertEquals(0, getSegmentNumber(1.seconds, min5))
            assertEquals(19, getSegmentNumber(299.seconds, min5))
            assertEquals(19, getSegmentNumber(min5, min5))
        }
    }

    fun LocalTime.assertThatDiffersLessThan(other: LocalTime, diff: Duration) {
        assertThat(abs(this.toMillisecondOfDay() - other.toMillisecondOfDay()).toLong()).isLessThan(diff.inWholeMilliseconds)
    }

    @Test
    fun testCalculateSunriseAndSunset() {
        listOf(
            Triple(
                LocalDate(2023, 6, 22),
                LocalTime(4, 30, 8),
                LocalTime(21, 18, 46)
            ),
            Triple(
                LocalDate(2021, 1, 1),
                LocalTime(8, 2, 23),
                LocalTime(15, 49, 56)
            ),
            Triple(
                LocalDate(2021, 6, 1),
                LocalTime(4, 36, 18),
                LocalTime(21, 5, 2)
            ),
            Triple(
                LocalDate(2021, 12, 1),
                LocalTime(7, 40, 3),
                LocalTime(15, 42, 46)
            ),
            Triple(
                LocalDate(2023, 3, 10),
                LocalTime(6, 19, 11),
                LocalTime(17, 47, 21)
            ),
            Triple(
                LocalDate(2023, 7, 15),
                LocalTime(4, 47, 57),
                LocalTime(21, 8, 6)
            ),
            Triple(
                LocalDate(2023, 9, 10),
                LocalTime(6, 17, 44),
                LocalTime(19, 20, 13)
            ),
        ).forEach { (date, expectedSunrise, expectedSunset) ->
            val (sunrise, sunset) = calculateSunriseAndSunset(
                poznanCoordinates.first,
                poznanCoordinates.second,
                date
            )

            logger.info { "$date: sunrise: $sunrise, sunset: $sunset" }

            assertTrue(sunrise < sunset)

            sunrise.assertThatDiffersLessThan(expectedSunrise, 1.minutes)
            sunset.assertThatDiffersLessThan(expectedSunset, 1.minutes)
        }
    }

    @Test
    fun testRoundToNearestMinute() {
        assertEquals(LocalTime(12, 0, 0), LocalTime(12, 0, 0).roundToNearestMinute())
        assertEquals(LocalTime(12, 1, 0), LocalTime(12, 0, 30).roundToNearestMinute())
        assertEquals(LocalTime(12, 1, 0), LocalTime(12, 0, 59).roundToNearestMinute())
        assertEquals(LocalTime(12, 0, 0), LocalTime(12, 0, 1).roundToNearestMinute())
        assertEquals(LocalTime(12, 0, 0), LocalTime(12, 0, 29).roundToNearestMinute())
        assertEquals(LocalTime(12, 24, 0), LocalTime(12, 23, 31).roundToNearestMinute())
        assertEquals(LocalTime(12, 46, 0), LocalTime(12, 45, 31).roundToNearestMinute())
        assertEquals(LocalTime(12, 35, 0), LocalTime(12, 34, 31).roundToNearestMinute())
        assertEquals(LocalTime(12, 59, 0), LocalTime(12, 59, 29).roundToNearestMinute())
        assertEquals(LocalTime(13, 0, 0), LocalTime(12, 59, 56).roundToNearestMinute())
    }

    @Test
    fun testFormatSunriseSunset() {
        assertEquals("13:45", LocalTime(13, 45, 52).formatJustHoursMinutes())
        assertEquals(" 6:21", LocalTime(6, 21, 1).formatJustHoursMinutes())
        assertEquals(" 7:02", LocalTime(7, 2, 1).formatJustHoursMinutes())
    }
}