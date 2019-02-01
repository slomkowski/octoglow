package eu.slomkowski.octoglow.octoglowd

import mu.KLogging
import org.junit.jupiter.api.Test

class BrightnessDaemonTest {
    companion object : KLogging()

    private val poznanCoordinates = 52.395869 to 16.929220

    //todo tests
    @Test
    fun testCalculateBrightnessFraction() {

//        fun cr(sleepTime : LocalTime, date : LocalDate, time: LocalTime)
//                = getPartOfTheDay(poznanCoordinates.first, poznanCoordinates.second, sleepTime, date.atTime(time))

//        (LocalTime.of(23, 31) to LocalDate.of(2018, 8, 24)).let { (st, d) ->
//            assertEquals(BrightnessDaemon.PartOfTheDay.MIDDAY, cr(st, d, LocalTime.of(12, 34)))
//            assertEquals(BrightnessDaemon.PartOfTheDay.TRANSITION_TO_SLEEPING_TIME, cr(st, d, LocalTime.of(0, 0)))
//            assertEquals(BrightnessDaemon.PartOfTheDay.TRANSITION_TO_SLEEPING_TIME, cr(st, d, LocalTime.of(0, 1)))
//            assertEquals(BrightnessDaemon.PartOfTheDay.SLEEPING_TIME, cr(st, d, LocalTime.of(0, 2)))
//            assertEquals(BrightnessDaemon.PartOfTheDay.SLEEPING_TIME, cr(st, d, LocalTime.of(3, 34)))
//            assertEquals(BrightnessDaemon.PartOfTheDay.SLEEPING_TIME, cr(st, d, LocalTime.of(5, 34)))
//            assertEquals(BrightnessDaemon.PartOfTheDay.TRANSITION_TO_MIDDAY, cr(st, d, LocalTime.of(5, 36)))
//            assertEquals(BrightnessDaemon.PartOfTheDay.TRANSITION_TO_MIDDAY, cr(st, d, LocalTime.of(6, 12)))
//        }

    }

    @Test
    fun testCalculateRanges() {
//        testForEveryHour(calculateRanges(LocalTime.of(7, 32), LocalTime.of(17, 23), LocalTime.of(23, 3)))
//        testForEveryHour(calculateRanges(LocalTime.of(7, 32), LocalTime.of(17, 23), LocalTime.of(1, 32)))
//        testForEveryHour(calculateRanges(LocalTime.of(4, 14), LocalTime.of(21, 12), LocalTime.of(4, 32))) // what if go to sleep very late?
    }

//    private fun testForEveryHour(ranges: List<BrightnessDaemon.PodRange>) {
//        logger.debug { "Ranges:\n" + ranges.joinToString("\n") }
//        (0 until 24 * 60 * 60).map { LocalTime.ofSecondOfDay(it.toLong()) }.forEach {
//            val t = Duration.between(LocalTime.MIN, it)
//            try {
//                assertTrue(ranges.any { r -> r.surrounds(t) || r.surrounds(t.plusDays(1)) },"$it doesn't find in ranges" )
//            }catch (e : Exception) {
//                fail("$it doesn't find in ranges:\n"+ ranges.joinToString("\n"), e)
//            }
//        }
//    }
}