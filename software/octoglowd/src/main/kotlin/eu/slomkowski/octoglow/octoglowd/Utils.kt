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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinInstant
import mu.KLogger
import org.shredzone.commons.suncalc.SunTimes
import java.io.*
import java.nio.charset.StandardCharsets
import java.time.*
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

const val DEGREE: Char = '\u00B0'

val WARSAW_ZONE_ID: ZoneId = ZoneId.of("Europe/Warsaw")

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

fun isSleeping(start: LocalTime, duration: Duration, now: LocalTime): Boolean {
    val sleepTime = Duration.between(LocalTime.MIN, start)
    val sleepRange = sleepTime..(sleepTime + duration)
    val nowd = Duration.between(LocalTime.MIN, now)
    return nowd in sleepRange || nowd.plusDays(1) in sleepRange
}

fun calculateSunriseAndSunset(latitude: Double, longitude: Double, ts: LocalDate): Pair<LocalTime, LocalTime> {

    val sunTimes = SunTimes.compute()
        .at(latitude, longitude)
        .on(ts.year, ts.monthNumber, ts.dayOfMonth)
        .oneDay()
        .execute()

    val (sunrise, sunset) = listOf(sunTimes.rise, sunTimes.set).map {
        checkNotNull(it?.toLocalDateTime()) { "cannot calculate sunrise/sunset for $ts." }.toLocalTime()
    }
    check(sunset > sunrise)

    return sunrise to sunset
}

fun formatHumidity(h: Double?): String = when (h) {
    null -> "H:--%"
    else -> String.format("H:%2.0f%%", h)
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
@ExperimentalTime
fun getSegmentNumber(currentTime: kotlin.time.Duration, maxTime: kotlin.time.Duration): Int =
    floor(20.0 * (currentTime.inMilliseconds / maxTime.inMilliseconds)).roundToInt().coerceIn(0, 19)

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

@ExperimentalTime
suspend fun ringBellIfNotSleeping(
    config: Config,
    logger: KLogger,
    hardware: Hardware,
    ringDuration: Duration
) = when (isSleeping(config[SleepKey.startAt], config[SleepKey.duration], LocalTime.now())) {
    false -> try {
        hardware.clockDisplay.ringBell(ringDuration)
    } catch (ringException: Exception) {
        logger.error(ringException) { "Cannot ring a bell, perhaps I2C error;" }
    }
    true -> logger.warn { "Skipping ringing because it is sleep time." }
}

@ExperimentalTime
suspend fun handleException(
    config: Config,
    logger: KLogger,
    hardware: Hardware,
    coroutineContext: CoroutineContext,
    e: Throwable
) {
    logger.error(e) { "Exception caught in $coroutineContext." }
    if (config[ConfKey.ringAtError]) {
        ringBellIfNotSleeping(config, logger, hardware, Duration.ofMillis(150))
    } else {
        logger.error(e) { "Ringing at error disabled, only logging stack trace;" }
    }
}

fun List<Int>.toI2CBuffer(): I2CBuffer = I2CBuffer(this.size).apply {
    require(this@toI2CBuffer.isNotEmpty())
    this@toI2CBuffer.forEachIndexed { index, value -> this.set(index, value) }
}

fun Date.toLocalDateTime(): LocalDateTime = toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()


fun OffsetDateTime.toLocalDateTimeInSystemTimeZone(): LocalDateTime {
    val offset = ZoneId.systemDefault().rules.getOffset(toInstant())
    return withOffsetSameInstant(offset).toLocalDateTime()
}

fun I2CBuffer.toByteArray(): ByteArray = (0 until length).map { get(it).toByte() }.toByteArray()

fun I2CBuffer.set(index: Int, v: Byte): I2CBuffer = this.set(index, v.toInt())

fun I2CBuffer.contentToString(): String =
    (0 until this.length).map { this[it] }.joinToString(" ", prefix = "[", postfix = "]")

fun I2CBuffer.contentToBitString(): String =
    this.toList().joinToString(" ") { it.toString(2).padStart(8, '0') }

fun I2CBuffer.toList(): List<Int> = (0 until this.length).map { this[it] }.toList()

fun InputStream.readToString(): String = this.bufferedReader(StandardCharsets.UTF_8).readText()

fun kotlinx.datetime.LocalDateTime.toLocalDate() = LocalDate(year, month, dayOfMonth)

fun now(): Instant = java.time.Instant.now().toKotlinInstant()
