package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.dvlopt.linux.i2c.I2CBuffer
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import mu.KLogger
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.*
import kotlin.time.DurationUnit


const val DEGREE: Char = '\u00B0'

@Deprecated("use kotlinx.datetime")
val WARSAW_ZONE_ID_jvm: ZoneId = ZoneId.of("Europe/Warsaw")

val WARSAW_ZONE_ID: TimeZone = TimeZone.of("Europe/Warsaw")


val jsonSerializer = kotlinx.serialization.json.Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

val httpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }

    install(JsonFeature) {
        serializer = KotlinxSerializer(jsonSerializer)
    }
}

fun LocalDate.toCalendar() = Calendar.getInstance().also {
    val zonedDateTime: ZonedDateTime = this.toJavaLocalDate().atStartOfDay(ZoneId.systemDefault())
    val instant = zonedDateTime.toInstant()
    val date = Date.from(instant)
    val calendar = Calendar.getInstance()
    calendar.time = date
}

/**
 * This is implementation of sunrise/sunset algorithm:
 * https://web.archive.org/web/20161202180207/http://williams.best.vwh.net/sunrise_sunset_algorithm.htm
 */
fun calculateSunriseAndSunset(latitude: Double, longitude: Double, date: LocalDate): Pair<LocalTime, LocalTime> {

    fun toLocalTime(ut2: Double): LocalTime =
        Instant.fromEpochMilliseconds(date.toEpochDays() * 24L * 60 * 60 * 1000 + (1000.0 * ut2).roundToLong()).toLocalDateTime(TimeZone.currentSystemDefault()).time

    fun containIn(v: Double, m: Double) = v - floor(v / m) * m

    fun asinDeg(v: Double) = asin(v) * 180.0 / PI
    fun sinDeg(deg: Double) = sin(deg * PI / 180.0)
    fun cosDeg(deg: Double) = cos(deg * PI / 180.0)
    fun acosDeg(v: Double) = acos(v) * 180.0 / PI
    fun tanDeg(deg: Double) = tan(deg * PI / 180.0)
    fun atanDeg(v: Double) = atan(v) * 180.0 / PI

    fun calc(calculateSunrise: Boolean): Double {
        val lngHour = longitude / 15.0
        val t = when (calculateSunrise) {
            true -> date.dayOfYear + ((6.0 - lngHour) / 24.0)
            false -> date.dayOfYear + ((18.0 - lngHour) / 24.0)
        }

        val meanAnomaly = (0.9856 * t) - 3.289
        val trueLongitude = containIn(meanAnomaly + (1.916 * sinDeg(meanAnomaly)) + (0.020 * sinDeg(2 * meanAnomaly)) + 282.634, 360.0)

        var rightAscension = containIn(atanDeg(0.91764 * tanDeg(trueLongitude)), 360.0)

        val trueLongitudeQuadrant = (floor(trueLongitude / 90)) * 90.0
        val rightAscensionQuadrant = (floor(rightAscension / 90)) * 90.0
        rightAscension = (rightAscension + (trueLongitudeQuadrant - rightAscensionQuadrant)) / 15.0

        val sinDec = 0.39782 * sinDeg(trueLongitude)
        val cosDec = cosDeg(asinDeg(sinDec))

        val cosHourAngle = (cosDeg(90.833) - (sinDec * sinDeg(latitude))) / (cosDec * cosDeg(latitude))

        val hourAngle = when (calculateSunrise) {
            true -> 360.0 - acosDeg(cosHourAngle)
            false -> acosDeg(cosHourAngle)
        } / 15.0

        val localMeanTime = hourAngle + rightAscension - (0.06571 * t) - 6.622

        val universalMeanTime = localMeanTime - lngHour

        return containIn(universalMeanTime, 24.0) * 3600.0
    }

    return toLocalTime(calc(true)) to toLocalTime(calc(false))
}

fun LocalTime.roundToNearestMinute(): LocalTime {
    val div = 60_000
    val base = (floor(this.toMillisecondOfDay().toDouble() / div) * div).roundToInt()

    return LocalTime.fromMillisecondOfDay(
        if ((this.toMillisecondOfDay() % div) < div / 2) {
            base
        } else {
            base + div
        }
    )
}

fun formatHumidity(h: Double?): String = when (h) {
    null -> "--%"
    else -> String.format("%2.0f%%", h)
}

fun formatTemperature(t: Double?): String = when (t) {
    null -> "---.-${DEGREE}C"
    else -> String.format("%+5.1f${DEGREE}C", t)
}

fun formatPressure(t: Double?): String = when (t) {
    null -> "---- hPa"
    else -> String.format("%4.0f hPa", t)
}

/**
 * Used to calculate which segment to light of the upper progress bar on front display.
 */
fun getSegmentNumber(currentTime: kotlin.time.Duration, maxTime: kotlin.time.Duration): Int =
    floor(20.0 * (currentTime.toDouble(DurationUnit.MILLISECONDS) / maxTime.toDouble(DurationUnit.MILLISECONDS))).roundToInt().coerceIn(0, 19)

suspend fun <T : Any> trySeveralTimes(numberOfTries: Int, logger: KLogger, func: suspend (tryNo: Int) -> T): T {
    require(numberOfTries > 0)
    (1..numberOfTries).forEach { tryNo ->
        try {
            return func(tryNo)
        } catch (e: Exception) {
            if (tryNo == numberOfTries) {
                throw Exception("number of tries $numberOfTries exhausted, error: $e", e)
            } else {
                delay(70)
                logger.warn { "Operation $func failed with $e (try $tryNo/$numberOfTries)." }
            }
        }
    }
    throw IllegalStateException() // cannot be ever called
}

suspend fun handleException(
    config: Config,
    logger: KLogger,
    hardware: Hardware,
    coroutineContext: CoroutineContext,
    e: Throwable
) {
    logger.error(e) { "Exception caught in $coroutineContext." }
}

fun List<Int>.toI2CBuffer(): I2CBuffer = I2CBuffer(this.size).apply {
    require(this@toI2CBuffer.isNotEmpty())
    this@toI2CBuffer.forEachIndexed { index, value -> this.set(index, value) }
}

fun I2CBuffer.toByteArray(): ByteArray = (0 until length).map { get(it).toByte() }.toByteArray()

fun I2CBuffer.set(index: Int, v: Byte): I2CBuffer = this.set(index, v.toInt())

fun I2CBuffer.contentToString(): String =
    (0 until this.length).map { this[it] }.joinToString(" ", prefix = "[", postfix = "]")

fun I2CBuffer.contentToBitString(): String =
    this.toList().joinToString(" ") { it.toString(2).padStart(8, '0') }

fun I2CBuffer.toList(): List<Int> = (0 until this.length).map { this[it] }.toList()

fun InputStream.readToString(): String = this.bufferedReader(StandardCharsets.UTF_8).readText()

fun LocalDateTime.toLocalDate() = LocalDate(year, month, dayOfMonth)
fun LocalDateTime.toLocalTime() = LocalTime(hour, minute, second, nanosecond)

fun now(): Instant = Clock.System.now()

fun LocalTime.formatJustHoursMinutes() = "${hour.toString().padStart(2, ' ')}:${minute.toString().padStart(2, '0')}"
