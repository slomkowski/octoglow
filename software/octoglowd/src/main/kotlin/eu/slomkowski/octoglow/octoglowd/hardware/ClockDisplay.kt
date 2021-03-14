package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToString
import eu.slomkowski.octoglow.octoglowd.toList
import io.dvlopt.linux.i2c.I2CBuffer
import kotlinx.coroutines.delay
import mu.KLogging
import java.time.Duration

data class OutdoorWeatherReport(
    val channel: Int,
    val temperature: Double,
    val humidity: Double,
    val batteryIsWeak: Boolean,
    val alreadyReadFlag: Boolean,
    val manualTx: Boolean
) {

    init {
        require(channel in (1..3))
        require(temperature in -10.0..30.0) //todo further range
        require(humidity in 0.0..100.0)
    }

    companion object : KLogging() {
        private const val VALID_MEASUREMENT_FLAG = 1 shl 1
        private const val ALREADY_READ_FLAG = 1 shl 2

        fun parse(buff: I2CBuffer): OutdoorWeatherReport? {
            require(buff.length == 7)
            require(buff[0] == 4)

            //todo crc?

            if ((buff[1] and VALID_MEASUREMENT_FLAG) == 0) {
                return null
            }

            val alreadyRead = (buff[1] and ALREADY_READ_FLAG) != 0

            val sensorId = buff[6] and 0b11
            val manualTx = (buff[3] and 0b1000) != 0
            val weakBattery = (buff[3] and 0b100) != 0

            val temperatureBits = (buff[4] shl 4) + ((buff[5] shr 4) and 0b1111)
            val temperaturePart = (temperatureBits.toDouble() - 1220.0) * 1.0 / 18.0 + 0.0

            val humidityPart = 10.0 * (buff[5] and 0b1111) + ((buff[6] shr 4) and 0b1111)

            try {
                return OutdoorWeatherReport(
                    sensorId,
                    temperaturePart,
                    humidityPart,
                    weakBattery,
                    alreadyRead,
                    manualTx
                ).apply {
                    logger.debug {
                        val buffContent =
                            buff.toList().subList(2, 7).map { it.toString(2).padStart(8, '0') }.joinToString(" ")
                        "RAW: $buffContent, $this"
                    }
                }
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException(
                    "insane values despite valid flag set: T: $temperaturePart, H: $humidityPart. Buffer: ${buff.contentToString()}",
                    e
                )
            }
        }
    }
}

class ClockDisplay(hardware: Hardware) : I2CDevice(hardware, 0x10), HasBrightness {

    companion object : KLogging() {
        private const val UPPER_DOT: Int = 1 shl (14 % 8)
        private const val LOWER_DOT: Int = 1 shl (13 % 8)
    }

    override suspend fun setBrightness(brightness: Int) {
        doWrite(3, brightness)
    }

    override suspend fun initDevice() {
        doWrite(2, 0, 0)
    }

    override suspend fun closeDevice() {
        setBrightness(3)
        doWrite(2, 0, 0)
        doWrite(1, 45, 45, 45, 45)
    }

    suspend fun getOutdoorWeatherReport(): OutdoorWeatherReport? {
        val readBuffer = doTransaction(I2CBuffer(1).set(0, 4), 7)
        check(readBuffer[0] == 4)
        logger.debug { "Report buffer: " + readBuffer.contentToString() }
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

        doWrite(
            1, when (hours < 10) {
                true -> ' '.toInt()
                else -> 0x30 + hours / 10
            }, 0x30 + hours % 10, 0x30 + minutes / 10, 0x30 + minutes % 10, dots
        )
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