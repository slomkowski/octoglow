package eu.slomkowski.octoglow.octoglowd.daemon

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import mu.KLogging
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.time.ExperimentalTime

@ExperimentalTime
class BrightnessDaemon(
    private val config: Config,
    private val database: DatabaseLayer,
    private val hardware: Hardware
) : Daemon(config, hardware, logger, Duration.ofSeconds(30)) {

    data class BrightnessMode(
        val isDay: Boolean,
        val isSleeping: Boolean,
        val brightness: Int
    )

    companion object : KLogging() {

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
            require(sunset.isAfter(sunrise))

            val sr = Duration.between(LocalTime.MIN, sunrise)
            val ss = Duration.between(LocalTime.MIN, sunset)
            val dayRange = sr..ss
            val nowd = Duration.between(LocalTime.MIN, now)

            val isDay = nowd in dayRange
            val isSleeping = isSleeping(goToSleep, sleepDuration, now)

            return brightnessModes.first { it.isDay == isDay && it.isSleeping == isSleeping }.brightness
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
        val br = (forced ?: calculateBrightnessFraction(ZonedDateTime.now())).coerceIn(1, 5)
        logger.debug { "Setting brightness to $br." }
        hardware.setBrightness(br)
    }

    fun calculateBrightnessFraction(ts: ZonedDateTime): Int {

        val sleepStart = config[SleepKey.startAt]
        val sleepDuration = config[SleepKey.duration]

        //todo should check if geo is within timezone
        val (sunrise, sunset) = calculateSunriseAndSunset(
            config[GeoPosKey.latitude],
            config[GeoPosKey.longitude],
            ts.toLocalDate().toKotlinLocalDate()
        )
        logger.debug { "On ${ts.toLocalDate()} sun is up from $sunrise to $sunset." }

        val br = calculateFromData(sunrise, sunset, sleepStart, sleepDuration, ts.toLocalTime())

        logger.debug { "Assuming $sleepDuration sleep starts at $sleepStart, the current brightness is $br." }

        return br
    }
}
