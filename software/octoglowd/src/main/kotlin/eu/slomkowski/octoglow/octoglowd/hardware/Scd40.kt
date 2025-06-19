package eu.slomkowski.octoglow.octoglowd.hardware

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class Scd40(hardware: Hardware) : I2CDevice(hardware, 0x62, logger) {

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val START_PERIODIC_MEASUREMENT = 0x21b1u
        private const val STOP_PERIODIC_MEASUREMENT = 0x3F86u
        private const val READ_MEASUREMENT = 0xEC05u

        private const val SET_TEMPERATURE_OFFSET = 0x241Du
        private const val REINIT = 0x3646u
        private const val GET_SERIAL_NUMBER = 0x3682u

        inline fun splitToBytes(command: Int): IntArray = intArrayOf((command shr 8) and 0xff, command and 0xff)

        inline fun calculateCrc8(value: Int): Int {
            var crc = 0xff

            for (byte in splitToBytes(value)) {
                crc = 0xff and (crc xor byte)
                repeat(8) {
                    crc = if (crc and 0x80 != 0) {
                        (crc shl 1) xor 0x31
                    } else {
                        crc shl 1
                    } and 0xff
                }
            }

            return crc
        }
    }

    override suspend fun initDevice() {
        try {
            val serialNumber = readSerialNumber()
            logger.info { "Serial number is $serialNumber" }
        } catch (e: Exception) {
            logger.error { "Unable to read serial number." }
        }
    }

    override suspend fun closeDevice() {
        TODO()
    }

    suspend fun readSerialNumber(): Long {
        val result = devRead(0x3682, 3)
        return (result[0].toLong() shl 32) or (result[1].toLong() shl 16) or (result[2].toLong() shl 8)
    }

    private suspend fun devSendCommand(command: Int) {
        doWrite(*splitToBytes(command))
    }

    private suspend fun devWrite(command: Int, value: Int) {
        doWrite(*splitToBytes(command), *splitToBytes(value), calculateCrc8(value))
    }

    // todo add delay
    private suspend fun devRead(command: Int, numberOfWords: Int): IntArray {
        require(numberOfWords in 1..3)
        val readBuffer = doTransaction(splitToBytes(command), numberOfWords * 3)

        return (0..<numberOfWords).map { idx ->
            val offset = 3 * idx
            val readValue = readBuffer[offset + 0] shl 8 + readBuffer[offset + 1]
            val calculatedCrc8 = calculateCrc8(readValue)
            check(calculatedCrc8 == readBuffer[offset + 2]) { "SCD40 CRC8 mismatch, command $command" }
            readValue
        }.toIntArray()
    }
}