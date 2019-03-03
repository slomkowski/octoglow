package eu.slomkowski.octoglow.octoglowd

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.dvlopt.linux.i2c.I2CBuffer
import mu.KLogger

const val DEGREE: Char = '\u00B0'

val jacksonObjectMapper: ObjectMapper = com.fasterxml.jackson.databind.ObjectMapper()
        .registerModules(JavaTimeModule(), KotlinModule())
        .configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun formatHumidity(h: Double?): String = when (h) {
    null -> "H:--%"
    else -> String.format("H:%2.0f%%", h)
}

fun formatTemperature(t: Double?): String = when (t) {
    null -> "---.-${DEGREE}C"
    else -> String.format("%+5.1f${DEGREE}C", t)
}

suspend fun <T : Any> trySeveralTimes(numberOfTries: Int, logger: KLogger, func: suspend (tryNo: Int) -> T): T {
    require(numberOfTries > 0)
    (1..numberOfTries).forEach { tryNo ->
        try {
            return func(tryNo)
        } catch (e: Exception) {
            if (tryNo == numberOfTries) {
                throw Exception("number of tries $numberOfTries exhausted, error: $e", e)
            } else {
                logger.warn { "Operation $func failed with $e (try $tryNo/$numberOfTries)" }
            }
        }
    }
    throw IllegalStateException() // cannot be ever called
}

fun List<Int>.toI2CBuffer(): I2CBuffer = I2CBuffer(this.size).apply {
    require(this@toI2CBuffer.isNotEmpty())
    this@toI2CBuffer.forEachIndexed { index, value -> this.set(index, value) }
}

fun I2CBuffer.set(index: Int, v: Byte): I2CBuffer = this.set(index, v.toInt())

fun I2CBuffer.contentToString(): String = (0 until this.length).map { this[it] }.joinToString(" ", prefix = "[", postfix = "]")

fun I2CBuffer.toList(): List<Int> = (0 until this.length).map { this[it] }.toList()