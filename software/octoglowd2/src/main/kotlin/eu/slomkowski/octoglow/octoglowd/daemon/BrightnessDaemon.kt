package eu.slomkowski.octoglow.octoglowd.daemon

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.GeoPosKey
import eu.slomkowski.octoglow.octoglowd.SleepKey
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import mu.KLogging
import org.shredzone.commons.suncalc.SunTimes
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*

class BrightnessDaemon(
        private val config: Config,
        private val hardware: Hardware) : Daemon(Duration.ofSeconds(30)) {

    data class BrightnessMode(
            val isDay: Boolean,
            val isSleeping: Boolean,
            val brightness: Int)

    companion object : KLogging() {

        private val brightnessModes = setOf(
                BrightnessMode(true, false, 5),
                BrightnessMode(true, true, 4),
                BrightnessMode(false, false, 3),
                BrightnessMode(false, true, 1))

        fun calculateFromData(sunrise: LocalTime,
                              sunset: LocalTime,
                              goToSleep: LocalTime,
                              sleepDuration: Duration,
                              now: LocalTime): Int {
            require(sunset.isAfter(sunrise))

            val sr = Duration.between(LocalTime.MIN, sunrise)
            val ss = Duration.between(LocalTime.MIN, sunset)
            val dayRange = sr..ss
            val nowd = Duration.between(LocalTime.MIN, now)

            val sleepTime = Duration.between(LocalTime.MIN, goToSleep)
            val sleepRange = sleepTime..(sleepTime + sleepDuration)

            val isDay = nowd in dayRange
            val isSleeping = nowd in sleepRange || nowd.plusDays(1) in sleepRange

            return brightnessModes.first { it.isDay == isDay && it.isSleeping == isSleeping }.brightness
        }

        private fun Date.toLocalDateTime(): LocalDateTime = toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    override suspend fun pool() {
        val br = calculateBrightnessFraction(LocalDateTime.now()).coerceIn(1, 5)
        logger.debug { "Setting brightness to $br." }
        hardware.setBrightness(br)
    }

    fun calculateBrightnessFraction(ts: LocalDateTime): Int {

        val sleepStart = config[SleepKey.startAt]
        val sleepDuration = config[SleepKey.duration]

        val sunTimes = SunTimes.compute()
                .at(config[GeoPosKey.latitude], config[GeoPosKey.longitude])
                .on(ts.year, ts.monthValue, ts.dayOfMonth)
                .oneDay()
                .execute()

        val (sunrise, sunset) = listOf(sunTimes.rise, sunTimes.set).map {
            checkNotNull(it?.toLocalDateTime()) { "cannot calculate sunrise/sunset for $ts." }.toLocalTime()
        }
        check(sunset > sunrise)
        logger.debug { "On ${ts.toLocalDate()} sun is up from $sunrise to $sunset." }

        val br = calculateFromData(sunrise, sunset, sleepStart, sleepDuration, ts.toLocalTime())

        logger.debug { "Assuming $sleepDuration sleep starts at $sleepStart, the current brightness is $br." }

        return br
    }
}
