package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.BrightnessController.Companion.calculateBrightnessFraction
import eu.slomkowski.octoglow.octoglowd.BrightnessController.Companion.calculateRanges
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertTrue

class BrightnessControllerTest {
    companion object : KLogging()

    @Test
    fun testCalculateBrightnessFraction() {
        val r = calculateBrightnessFraction(LocalDateTime.of(2019, 1, 28, 1, 50), LocalTime.of(23, 16))
        logger.debug { "Found $r." }
        assertNotNull(r)
    }

    @Test
    fun testCalculateRanges() {
        testForEveryHour(calculateRanges(LocalTime.of(7, 32), LocalTime.of(17, 23), LocalTime.of(23, 3), Duration.ofHours(1)))
    }

    private fun testForEveryHour(ranges: List<BrightnessController.PodRange>) {
        (0 until 24 * 60 * 60).map { LocalTime.ofSecondOfDay(it.toLong()) }.forEach {
            val t = Duration.between(LocalTime.MIN, it)
            assertTrue { ranges.any { r -> r.surrounds(t) || r.surrounds(t.plusDays(1)) } }
        }
    }
}