package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToString
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import mu.KLogging
import java.time.Duration

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
        private const val WEAK_BATTERY_FLAG = 1 shl 0
        private const val VALID_MEASUREMENT_FLAG = 1 shl 1
        private const val ALREADY_READ_FLAG = 1 shl 2

        fun parse(buff: I2CBuffer): OutdoorWeatherReport? {
            require(buff.length == 5)

            if ((buff[4] and VALID_MEASUREMENT_FLAG) == 0) {
                return null
            }
            // T: 6517.3, H: 1.0. Buffer: [4 149 254 1 2]
            // T: 6510.9, H: 1.0. Buffer:  [4 85 254 1 2]
            // T: 6543.6, H: 68.0. Buffer: [4 156 255 68 6]     prawidłowe: 23.2 stopnie, 45% wilgotnosć
            val temperaturePart = (256 * buff[2] + buff[1]).toDouble() / 10.0
            val humidityPart = buff[3].toDouble()

            try {
                return OutdoorWeatherReport(
                        temperaturePart,
                        humidityPart,
                        (buff[4] and WEAK_BATTERY_FLAG) != 0,
                        (buff[4] and ALREADY_READ_FLAG) != 0)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("insane values despite valid flag set: T: $temperaturePart, H: $humidityPart. Buffer: ${buff.contentToString()}", e)
            }
        }
    }
}

class ClockDisplay(i2cMutex: Mutex, i2c: I2CBus) : I2CDevice(i2cMutex, i2c, 0x10), HasBrightness {

    companion object : KLogging() {
        private const val UPPER_DOT: Int = 1 shl (14 % 8)
        private const val LOWER_DOT: Int = 1 shl (13 % 8)
    }

    override suspend fun setBrightness(brightness: Int) {
        doWrite(3, brightness)
    }

    init {
        runBlocking {
            doWrite(2, 0, 0)
        }
    }

    override fun close() {
        runBlocking {
            setBrightness(3)
            doWrite(2, 0, 0)
            doWrite(1, 45, 45, 45, 45)
        }
    }

    suspend fun getOutdoorWeatherReport(): OutdoorWeatherReport? {
        val readBuffer = doTransaction(I2CBuffer(1).set(0, 4), 5)
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

        doWrite(1, when (hours < 10) {
            true -> ' '.toInt()
            else -> 0x30 + hours / 10
        }, 0x30 + hours % 10, 0x30 + minutes / 10, 0x30 + minutes % 10, dots)
    }

    suspend fun ringBell(duration: Duration) {
        require(!duration.isNegative)
        require(!duration.isZero)

        logger.info { "Ringing for ${duration.toMillis()} ms." }

        try {
            doWrite(2, 0, 1)
            delay(duration.toMillis())
        } finally {
            doWrite(2, 0, 0)
        }
    }
}
