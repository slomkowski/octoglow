@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd

import io.github.oshai.kotlinlogging.KLogger
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.datetime.*
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.math.*
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime


const val DEGREE: Char = '\u00B0'

val WARSAW_ZONE_ID: TimeZone = TimeZone.of("Europe/Warsaw")

val MANY_WHITESPACES_REGEX = Regex("\\s+")

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

    install(UserAgent) {
        agent = "octoglowd/1.0 (Linux; Octoglow; +https://slomkowski.eu/projects/octoglow-vfd-fallout-inspired-display)"
    }

    install(ContentNegotiation) {
        json(jsonSerializer)
    }
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

fun formatPpmConcentration(t: Double?): String = when (t) {
    null -> "---- ppm"
    else -> String.format("%4.0f ppm", t)
}


/**
 * Used to calculate which segment to light of the upper progress bar on the front display.
 */
fun getSegmentNumber(currentTime: kotlin.time.Duration, maxTime: kotlin.time.Duration): Int =
    floor(20.0 * (max(currentTime.toDouble(DurationUnit.MILLISECONDS), 0.0) / maxTime.toDouble(DurationUnit.MILLISECONDS))).roundToInt().coerceIn(0, 19)

suspend fun <T : Any> trySeveralTimes(
    numberOfTries: Int,
    logger: KLogger,
    funcDescription: String,
    func: suspend (tryNo: Int) -> T
): T {
    require(numberOfTries > 0)
    require(funcDescription.isNotEmpty())

    (1..numberOfTries).forEach { tryNo ->
        try {
            return func(tryNo)
        } catch (e: Exception) {
            if (tryNo == numberOfTries) {
                throw Exception("number of tries $numberOfTries exhausted: ${e.message}", e)
            } else {
                delay(70)
                logger.warn { "Operation '$funcDescription' failed with '${e.message}' (try $tryNo/$numberOfTries)." }
            }
        }
    }
    error("cannot be ever called")
}


fun IntArray.contentToBitString(): String =
    this.joinToString(" ") { it.toString(2).padStart(8, '0') }


fun InputStream.readToString(): String = this.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

fun LocalTime.formatJustHoursMinutes() = "${hour.toString().padStart(2, ' ')}:${minute.toString().padStart(2, '0')}"

fun String.abbreviate(maxLength: Int): String {
    require(maxLength > 0) { "maxLength must be positive" }

    return when {
        length <= maxLength -> this
        maxLength <= 3 -> substring(0, maxLength)
        else -> substring(0, maxLength - 3) + "..."
    }
}

private val uppercaseLetters = Regex("([A-Z])")
fun toSnakeCase(s: String) = uppercaseLetters.replace(s, "_$1").uppercase().trim('_')