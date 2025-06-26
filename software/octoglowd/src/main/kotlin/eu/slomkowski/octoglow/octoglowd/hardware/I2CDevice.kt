package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToString
import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.I2CBuffer
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
abstract class I2CDevice(
    private val hardware: Hardware,
    private val i2cAddress: Int,
    private val logger: KLogger,
) {
    companion object {
        private val defaultDelayBetweenWriteAndRead = 1.milliseconds

        fun calculateCcittCrc8(data: IntArray, range: ClosedRange<Int>): Int {
            var crcValue = 0x00

            var i = range.start
            while (i <= range.endInclusive) {
                crcValue = 0xff and (crcValue xor data[i])
                repeat(8) {
                    crcValue = if (crcValue and 0x80 != 0) {
                        (crcValue shl 1) xor 0x07
                    } else {
                        crcValue shl 1
                    } and 0xff
                }
                i++
            }

            return crcValue
        }

        // todo zamienić na I2CBuffer?
        fun verifyResponse(reqBuff: I2CBuffer, respBuff: IntArray, crcAtTheFront: Boolean) {
            try {
                require(respBuff.size >= 2) { "response has to be at least 2 bytes" }

                val (received, range, commandBytePosition) = when (crcAtTheFront) {
                    true -> Triple(respBuff.first(), 1..<respBuff.size, 1)
                    false -> Triple(respBuff.last(), 0..respBuff.size - 2, 0)
                }

                val calculatedLocally = calculateCcittCrc8(respBuff, range)

                check(calculatedLocally == received) { "CRC mismatch, calculated CRC8 = $calculatedLocally, received = $received" }
                check(reqBuff[commandBytePosition] == respBuff[commandBytePosition]) { "invalid response, request number was ${reqBuff[commandBytePosition]}, got ${respBuff[commandBytePosition]}" }
            } catch (e: Exception) {
                error("problem with following request: ${reqBuff.contentToString()}, response: ${respBuff.contentToString()}: ${e.message}")
            }
        }
    }

    init {
        require(i2cAddress in 0..127)
    }

    abstract suspend fun initDevice()

    abstract suspend fun closeDevice()

    suspend fun doWrite(vararg bytes: Int) {
        doWrite(bytes.toI2CBuffer())
    }

    @OptIn(ExperimentalTime::class)
    suspend fun doWrite(writeBuffer: I2CBuffer) = hardware.doWrite(i2cAddress, writeBuffer)

    suspend fun doTransaction(
        command: IntArray,
        bytesToRead: Int,
        delayBetweenWriteAndRead: Duration = defaultDelayBetweenWriteAndRead,
    ): I2CBuffer {
        return doTransaction(command.toI2CBuffer(), bytesToRead, delayBetweenWriteAndRead)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun doTransaction(
        writeBuffer: I2CBuffer,
        bytesToRead: Int,
        delayBetweenWriteAndRead: Duration = defaultDelayBetweenWriteAndRead,
    ): I2CBuffer =
        hardware.doTransaction(i2cAddress, writeBuffer, bytesToRead, delayBetweenWriteAndRead)
}