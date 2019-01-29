package eu.slomkowski.octoglow.octoglowd

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.dvlopt.linux.i2c.I2CBuffer

const val DEGREE: Char = '\u00B0'

val jacksonObjectMapper : ObjectMapper = com.fasterxml.jackson.databind.ObjectMapper()
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

fun I2CBuffer.contentToString(): String = (0 until this.length).map { this[it] }.joinToString(" ", prefix = "[", postfix = "]")