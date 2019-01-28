package eu.slomkowski.octoglow.octoglowd

import mu.KLogging
import org.shredzone.commons.suncalc.SunTimes
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*

/**
 * This algorithm manages the brightness of the displays.
 */
class BrightnessController {

    enum class PartOfTheDay {
        MIDDAY,
        TRANSITION_TO_EVENING,
        EVENING,
        TRANSITION_TO_SLEEPING_TIME,
        SLEEPING_TIME,
        TRANSITION_TO_MIDDAY
    }

    class PodRange(val pod: PartOfTheDay, val start: Duration, end: Duration) {

        val end: Duration = when (end < start) {
            true -> end.plusDays(1)
            else -> end
        }

        init {
            require(!this.start.isNegative)
            require(!this.end.isNegative)
            require(this.start < this.end)
        }

        fun surrounds(d: Duration): Boolean = d in start..end
    }

    companion object : KLogging() {

        private val transitionDuration: Duration = Duration.ofHours(1)

        private fun Date.toLocalDateTime(): LocalDateTime = toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

        fun calculateBrightnessFraction(ts: LocalDateTime, goToSleep: LocalTime): PartOfTheDay {

            val sunTimes = SunTimes.compute()
                    .at(52.395869, 16.929220)
                    .on(ts.year, ts.monthValue, ts.dayOfMonth)
                    .oneDay()
                    .execute()

            val (sunrise, sunset) = listOf(sunTimes.rise, sunTimes.set).map {
                checkNotNull(it?.toLocalDateTime()) { "cannot calculate sunrise/sunset for $ts." }.toLocalTime()
            }
            check(sunset > sunrise)
            logger.debug { "On ${ts.toLocalDate()} sun is up from $sunrise to $sunset." }

            val ranges = calculateRanges(sunrise, sunset, goToSleep, transitionDuration)

            val t = Duration.between(LocalTime.MIN, ts.toLocalTime())

            val currentRange = checkNotNull(ranges.firstOrNull { it.surrounds(t) || it.surrounds(t.plusDays(1)) }) { "range not found for $ts" }

            return currentRange.pod
        }

        fun calculateRanges(sunrise: LocalTime, sunset: LocalTime, goToSleep: LocalTime, transition: Duration): List<PodRange> {
            require(sunset.isAfter(sunrise))
            require(transition < Duration.ofHours(3))

            val sr = Duration.between(LocalTime.MIN, sunrise)
            val ss = Duration.between(LocalTime.MIN, sunset)
            val htt = transition.dividedBy(2)

            //val isSleepAfterMidnight =
            val sleepTime = Duration.between(LocalTime.MIN, goToSleep).plus(when (goToSleep.isBefore(LocalTime.NOON)) {
                true -> Duration.ofDays(1)
                false -> Duration.ZERO
            })

            return listOf(
                    PodRange(PartOfTheDay.MIDDAY, sr + htt, ss - htt),
                    PodRange(PartOfTheDay.TRANSITION_TO_EVENING, ss - htt, ss + htt),
                    PodRange(PartOfTheDay.EVENING, ss + htt, sleepTime - htt),
                    PodRange(PartOfTheDay.TRANSITION_TO_SLEEPING_TIME, sleepTime - htt, sleepTime + htt),
                    PodRange(PartOfTheDay.SLEEPING_TIME, sleepTime + htt, sr - htt),
                    PodRange(PartOfTheDay.TRANSITION_TO_MIDDAY, sr - htt, sr + htt))
        }
    }
}