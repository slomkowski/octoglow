package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToBitString
import io.dvlopt.linux.i2c.I2CBuffer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.ExperimentalTime

data class RemoteSensorReport(
    val sensorId: Int,
    val temperature: Double,
    val humidity: Double,
    val batteryIsWeak: Boolean,
    val alreadyReadFlag: Boolean,
    val manualTx: Boolean,
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        private const val VALID_MEASUREMENT_FLAG = 1 shl 1
        private const val ALREADY_READ_FLAG = 1 shl 2

        fun toBitArray(i2CBuffer: I2CBuffer) = BooleanArray(40).apply {
            for (i in 0..39) {
                this[i] = i2CBuffer[2 + i / 8] and (0b10000000 shr (i % 8)) != 0
            }
        }

        /**
         * This code is ported from https://www.chaosgeordend.nl/documents/analyse_rf433.py
         */
        fun calculateChecksum(i2CBuffer: I2CBuffer): Boolean {

            var csum = 0x0
            var mask = 0xC
            val msg = toBitArray(i2CBuffer)
            val calculationBuffer = msg.copyOfRange(0, 8)
                .plus(msg.copyOfRange(36, 40))
                .plus(msg.copyOfRange(12, 36))

            val checksum = (i2CBuffer[3] shr 4) and 0xf

            for (b in calculationBuffer) {
                val bit = mask and 0x1
                mask = mask shr 1
                if (bit == 0x1) {
                    mask = mask xor 0x9
                }

                if (b) {
                    csum = csum xor mask
                }
            }

            csum = csum and 0xf

            return csum == checksum
        }

        fun parse(buff: I2CBuffer): RemoteSensorReport? {
            require(buff.length == 7)
            require(buff[0] == 4)

            if ((buff[1] and VALID_MEASUREMENT_FLAG) == 0) {
                return null
            }

            val alreadyRead = (buff[1] and ALREADY_READ_FLAG) != 0

            val sensorId = buff[6] and 0b11
            val manualTx = (buff[3] and 0b1000) != 0
            val weakBattery = (buff[3] and 0b100) != 0

            val temperatureBits = (buff[4] shl 4) + ((buff[5] shr 4) and 0b1111)
            val temperature = (temperatureBits.toDouble() - 1220.0) * 1.0 / 18.0 + 0.0

            val humidity = 10.0 * (buff[5] and 0b1111) + ((buff[6] shr 4) and 0b1111)

            val areValuesValid = (sensorId in (1..3)) && (temperature in -40.0..60.0) && (humidity in 0.0..100.0)

            if (!areValuesValid) {
                return null
            }

            if (!calculateChecksum(buff)) {
                return null
            }

            if (!alreadyRead) {
                logger.trace {
                    String.format(
                        "ch=%d,temp=%2.1f,hum=%2.0f%%,wb=%b,tx=%b,raw=%s.",
                        sensorId,
                        temperature,
                        humidity,
                        weakBattery,
                        manualTx,
                        buff.contentToBitString().drop(18)
                    )
                }
            }

            return RemoteSensorReport(
                sensorId,
                temperature,
                humidity,
                weakBattery,
                alreadyRead,
                manualTx
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
class ClockDisplay(hardware: Hardware) : I2CDevice(hardware, 0x10, logger), HasBrightness {

    companion object {
        private val logger = KotlinLogging.logger {}

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

    suspend fun retrieveRemoteSensorReport(): RemoteSensorReport? {
        val readBuffer = doTransaction(I2CBuffer(1).set(0, 4), 7)
        check(readBuffer[0] == 4)
        return RemoteSensorReport.parse(readBuffer)
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
                true -> ' '.code
                else -> 0x30 + hours / 10
            }, 0x30 + hours % 10, 0x30 + minutes / 10, 0x30 + minutes % 10, dots
        )
    }
}