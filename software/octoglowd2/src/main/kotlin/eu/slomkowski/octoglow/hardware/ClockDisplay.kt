package eu.slomkowski.octoglow.hardware

import eu.slomkowski.octoglow.contentToString
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.math.pow

data class OutdoorWeatherReport(
        val temperature: Double,
        val humidity: Double,
        val batteryIsWeak: Boolean,
        val alreadyReadFlag: Boolean) {
    init {
        require(temperature in -40.0..60.0)
        require(humidity in 0.0..100.0)
    }

    companion object {
        fun parse(buff: I2CBuffer): OutdoorWeatherReport {
            val temperaturePart = (256 * buff[2] + buff[1]).let {
                when (it > 0x100) {
                    true -> it.toDouble() - 2.0.pow(12)
                    false -> it.toDouble()
                } / 10.0
            }

            val humidityPart = buff[3].toDouble()

            try {
                return OutdoorWeatherReport(
                        temperaturePart,
                        humidityPart,
                        buff[4] == 1,
                        buff[5] == 1)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("invalid weather data: T: $temperaturePart, H: $humidityPart. Buffer: ${buff.contentToString()}", e)
            }
        }
    }
}

class ClockDisplay(ctx: CoroutineContext, i2c: I2CBus) : I2CDevice(ctx, i2c, 0x10), HasBrightness {

    companion object {
        private const val UPPER_DOT: Int = 1 shl (14 % 8)
        private const val LOWER_DOT: Int = 1 shl (13 % 8)
    }

    override suspend fun setBrightness(brightness: Int) {
        doWrite(3, brightness)
    }

    suspend fun getOutdoorWeatherReport(): OutdoorWeatherReport {
        val readBuffer = doTransaction(I2CBuffer(1).set(0, 4), 6)
        check(readBuffer[0] == 4)
        return OutdoorWeatherReport.parse(readBuffer)
    }

    suspend fun setDisplay(hours: Int, minutes: Int, upperDot: Boolean, lowerDot: Boolean) {
        require(hours in 0..24)
        require(minutes in 0..60)

        val dots = if (upperDot) {
            UPPER_DOT
        } else {
            0
        } or if (lowerDot) {
            LOWER_DOT
        } else {
            0
        }

        doWrite(1, 0x30 + hours / 10, 0x30 + hours % 10, 0x30 + minutes / 10, 0x30 + minutes % 10, dots)
    }
}
