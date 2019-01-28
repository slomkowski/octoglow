package eu.slomkowski.octoglow.octoglowd

import mu.KLogging
import org.shredzone.commons.suncalc.SunTimes
import java.time.*
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

    companion object :KLogging() {

        private val transitionDuration : Duration = Duration.ofHours(1)

        private fun Date.toLocalDateTime() : LocalDateTime =toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

        fun calculateBrightnessFraction(ts: LocalDateTime, goToSleep : LocalTime):PartOfTheDay  {

            val sunTimes = SunTimes.compute()
                    .at(52.395869, 16.929220)
                    .on(ts.year, ts.monthValue, ts.dayOfMonth)
                    .oneDay()
                    .execute()

            val sunrise = checkNotNull(sunTimes.rise?.toLocalDateTime()) {"cannot calculate sunrise for $ts."}
            val sunset = checkNotNull(sunTimes.set?.toLocalDateTime()) {"cannot calculate sunset for $ts."}
            check(sunset.isAfter(sunrise))

            logger.debug { "Times: $sunrise $sunset." }

            val halfTransition = transitionDuration.dividedBy(2)






        }
    }
}