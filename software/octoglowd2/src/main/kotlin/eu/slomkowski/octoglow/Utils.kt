package eu.slomkowski.octoglow

import io.dvlopt.linux.i2c.I2CBuffer

const val DEGREE: Char = '\u00B0'

fun formatHumidity(h: Double?): String = when (h) {
    null -> "H:--%"
    else -> String.format("H:%2.0f%%", h)
}

fun formatTemperature(t: Double?): String = when (t) {
    null -> "---.-${DEGREE}C"
    else -> String.format("%+5.1f${DEGREE}C", t)
}

fun I2CBuffer.contentToString() : String =    (0 until this.length).map { this[it] }.joinToString(" ", prefix = "[", postfix = "]")