package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.controller.BrightnessController
import mu.KLogging
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals

class BrightnessControllerTest {
    companion object : KLogging()

    private val poznanCoordinates = 52.395869 to 16.929220

    //todo tests
    @Test
    fun testCalculateBrightnessFraction() {
        fun cr(sleepTime : LocalTime, sleepDurationHours : Int, date : LocalDate, time: LocalTime)
                = BrightnessController.calculateFromData(LocalTime.of(7, 32), LocalTime.of(17, 23),
                sleepTime, Duration.ofHours(sleepDurationHours.toLong()), time)

        (LocalTime.of(23, 31) to LocalDate.of(2018, 8, 24)).let { (st, d) ->
            assertEquals(5, cr(st, d, LocalTime.of(12, 34)))
            assertEquals(3, cr(st, d, LocalTime.of(0, 0)))
            assertEquals(4, cr(st, d, LocalTime.of(0, 1)))
            assertEquals(3, cr(st, d, LocalTime.of(0, 2)))
            assertEquals(3, cr(st, d, LocalTime.of(3, 34)))
            assertEquals(3, cr(st, d, LocalTime.of(5, 34)))
            assertEquals(3, cr(st, d, LocalTime.of(5, 36)))
            assertEquals(9, cr(st, d, LocalTime.of(6, 12)))
        }

    }

    @Test
    fun testCalculateRanges() {
//        testForEveryHour(calculateRanges(LocalTime.of(7, 32), LocalTime.of(17, 23), LocalTime.of(23, 3)))
//        testForEveryHour(calculateRanges(LocalTime.of(7, 32), LocalTime.of(17, 23), LocalTime.of(1, 32)))
//        testForEveryHour(calculateRanges(LocalTime.of(4, 14), LocalTime.of(21, 12), LocalTime.of(4, 32))) // what if go to sleep very late?
    }

    private fun testForWholeDay(underTest : (LocalTime)->Unit) {
        (0 until 24 * 60 * 60).map { LocalTime.ofSecondOfDay(it.toLong()) }.forEach {
            underTest(it)
        }
    }
}