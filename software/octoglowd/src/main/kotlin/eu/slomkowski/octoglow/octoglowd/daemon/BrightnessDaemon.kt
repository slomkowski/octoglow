package eu.slomkowski.octoglow.octoglowd.daemon


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


class BrightnessDaemon(
    private val config: Config,
    private val database: DatabaseLayer,
    private val hardware: Hardware,
) : Daemon(logger, 1200.milliseconds) {

    // todo publish current brightness in a flow
    sealed class BrightnessValue {
        abstract val brightness: Int

        data class Auto(override val brightness: Int) : BrightnessValue()
        data class Manual(override val brightness: Int) : BrightnessValue()
    }

    private val _brightnessValue = MutableStateFlow<BrightnessValue>(BrightnessValue.Auto(3))
    val brightnessValue: StateFlow<BrightnessValue> = _brightnessValue

    private data class BrightnessMode(
        val isDay: Boolean,
        val isSleeping: Boolean,
        val lightSensor: Collection<LightSensor>,
        val brightness: Int,
    )

    enum class LightSensor(val valueRange: IntRange) {
        FULLY_LIGHT(800..Int.MAX_VALUE),
        INTERMEDIATE(200..800),
        FULLY_DARK(0..200),
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private val brightnessModes = setOf(
            BrightnessMode(
                isDay = true,
                isSleeping = false,
                lightSensor = setOf(LightSensor.FULLY_LIGHT, LightSensor.INTERMEDIATE),
                brightness = 5
            ),
            BrightnessMode(
                isDay = true,
                isSleeping = false,
                lightSensor = setOf(LightSensor.FULLY_DARK),
                brightness = 3
            ),

            BrightnessMode(
                isDay = true,
                isSleeping = true,
                lightSensor = setOf(LightSensor.FULLY_LIGHT, LightSensor.INTERMEDIATE),
                brightness = 4
            ),
            BrightnessMode(
                isDay = true,
                isSleeping = true,
                lightSensor = setOf(LightSensor.FULLY_DARK),
                brightness = 3
            ),

            BrightnessMode(
                isDay = false,
                isSleeping = false,
                lightSensor = setOf(LightSensor.FULLY_LIGHT),
                brightness = 4
            ),
            BrightnessMode(
                isDay = false,
                isSleeping = false,
                lightSensor = setOf(LightSensor.INTERMEDIATE),
                brightness = 3
            ),
            BrightnessMode(
                isDay = false,
                isSleeping = false,
                lightSensor = setOf(LightSensor.FULLY_DARK),
                brightness = 2
            ),

            BrightnessMode(
                isDay = false,
                isSleeping = true,
                lightSensor = setOf(LightSensor.FULLY_LIGHT),
                brightness = 4
            ),
            BrightnessMode(
                isDay = false,
                isSleeping = true,
                lightSensor = setOf(LightSensor.INTERMEDIATE),
                brightness = 3
            ),
            BrightnessMode(
                isDay = false,
                isSleeping = true,
                lightSensor = setOf(LightSensor.FULLY_DARK),
                brightness = 1
            ),
        )

        fun calculateFromData(
            sunrise: LocalTime,
            sunset: LocalTime,
            goToSleep: LocalTime,
            sleepDuration: Duration,
            now: LocalTime,
            lightCategory: LightSensor,
        ): Int {
            require(sunset > sunrise)

            val sr = sunrise.toSecondOfDay().seconds
            val ss = sunset.toSecondOfDay().seconds
            val dayRange = sr..ss
            val nowd = now.toSecondOfDay().seconds

            val isDay = nowd in dayRange
            val isSleeping = isSleeping(goToSleep, sleepDuration, now)

            return brightnessModes.first { it.isDay == isDay && it.isSleeping == isSleeping && lightCategory in it.lightSensor }.brightness
        }

        fun isSleeping(start: LocalTime, duration: Duration, now: LocalTime): Boolean {
            val sleepTime = start.toSecondOfDay().seconds

            val sleepRange = sleepTime..(sleepTime + duration)
            val nowd = now.toSecondOfDay().seconds

            return nowd in sleepRange || nowd.plus(1.days) in sleepRange
        }
    }

    @Volatile
    var forced: Int? = null
        private set

    suspend fun setForcedMode(v: Int?) {
        forced = v
        database.setChangeableSettingAsync(ChangeableSetting.BRIGHTNESS, v?.toString() ?: "AUTO")
        poll()
    }

    init {
        forced = runBlocking {
            database.getChangeableSettingAsync(ChangeableSetting.BRIGHTNESS).await()?.toIntOrNull()?.apply {
                hardware.setBrightness(this)
            }
        }
    }

    override suspend fun poll() {
        val br = (forced ?: run {
            val lightSensorValue = hardware.clockDisplay.retrieveLightSensorMeasurement()
            val lightSensorCategory = LightSensor.entries.first { lightSensorValue in it.valueRange }
            calculateBrightnessFraction(now(), lightSensorCategory)
        }).coerceIn(1, 5)
//        logger.debug { "Setting brightness to $br." }
        hardware.setBrightness(br)
        _brightnessValue.value = when (forced) {
            null -> BrightnessValue.Auto(br)
            else -> BrightnessValue.Manual(br)
        }
    }

    fun calculateBrightnessFraction(ts: Instant, lightSensorCategory: LightSensor): Int {

        val sleepStart = config.sleep.startAt
        val sleepDuration = config.sleep.duration

        val localDateTime = ts.toLocalDateTime(TimeZone.currentSystemDefault())

        //todo should check if geo is within timezone
        // todo wyliczać co kilka minut, publikować do flow albo zmiennej
        val (sunrise, sunset) = calculateSunriseAndSunset(
            config.geoPosition.latitude,
            config.geoPosition.longitude,
            localDateTime.date,
        )
        logger.trace { "On ${localDateTime.date} sun is up from $sunrise to $sunset." }

        val br = calculateFromData(sunrise, sunset, sleepStart, sleepDuration, localDateTime.time, lightSensorCategory)

        logger.trace { "Assuming $sleepDuration sleep starts at $sleepStart and light is $lightSensorCategory, the current brightness is $br." }

        return br
    }
}
