package eu.slomkowski.octoglow.octoglowd.daemon


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds


class BrightnessDaemon(
    private val config: Config,
    private val database: DatabaseLayer,
    private val hardware: Hardware
) : Daemon(config, hardware, logger, 30.seconds) {

    data class BrightnessMode(
        val isDay: Boolean,
        val isSleeping: Boolean,
        val brightness: Int
    )

    companion object {
        private val logger = KotlinLogging.logger {}

        private val brightnessModes = setOf(
            BrightnessMode(true, false, 5),
            BrightnessMode(true, true, 4),
            BrightnessMode(false, false, 3),
            BrightnessMode(false, true, 1)
        )

        fun calculateFromData(
            sunrise: LocalTime,
            sunset: LocalTime,
            goToSleep: LocalTime,
            sleepDuration: Duration,
            now: LocalTime
        ): Int {
            require(sunset > sunrise)

            val sr = sunrise.toSecondOfDay().seconds
            val ss = sunset.toSecondOfDay().seconds
            val dayRange = sr..ss
            val nowd = now.toSecondOfDay().seconds

            val isDay = nowd in dayRange
            val isSleeping = isSleeping(goToSleep, sleepDuration, now)

            return brightnessModes.first { it.isDay == isDay && it.isSleeping == isSleeping }.brightness
        }

        fun isSleeping(start: LocalTime, duration: Duration, now: LocalTime): Boolean {
            val sleepTime = start.toSecondOfDay().seconds

            val sleepRange = sleepTime..(sleepTime + duration)
            val nowd = now.toSecondOfDay().seconds

            return nowd in sleepRange || nowd.plus(1.days) in sleepRange
        }
    }

    var forced: Int? = null
        private set

    suspend fun setForcedMode(v: Int?) {
        forced = v
        database.setChangeableSettingAsync(ChangeableSetting.BRIGHTNESS, v?.toString() ?: "AUTO")
        pool()
    }

    init {
        forced = runBlocking {
            database.getChangeableSettingAsync(ChangeableSetting.BRIGHTNESS).await()?.toIntOrNull()?.apply {
                hardware.setBrightness(this)
            }
        }
    }

    override suspend fun pool() {
        val br = (forced ?: calculateBrightnessFraction(now())).coerceIn(1, 5)
        logger.debug { "Setting brightness to $br." }
        hardware.setBrightness(br)
    }

    fun calculateBrightnessFraction(ts: Instant): Int {

        val sleepStart = config.sleep.startAt
        val sleepDuration = config.sleep.duration

        val localDateTime = ts.toLocalDateTime(TimeZone.currentSystemDefault())

        //todo should check if geo is within timezone
        val (sunrise, sunset) = calculateSunriseAndSunset(
            config.geoPosition.latitude,
            config.geoPosition.longitude,
            localDateTime.date
        )
        logger.debug { "On ${localDateTime.date} sun is up from $sunrise to $sunset." }

        val br = calculateFromData(sunrise, sunset, sleepStart, sleepDuration, localDateTime.time)

        logger.debug { "Assuming $sleepDuration sleep starts at $sleepStart, the current brightness is $br." }

        return br
    }
}
