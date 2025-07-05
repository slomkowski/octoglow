package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.trySeveralTimes
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface I2cDevice {
    val i2cAddress: Int

    suspend fun initDevice()

    suspend fun closeDevice()
}

abstract class FactoryMadeI2cDevice(
    private val hardware: Hardware,
    final override val i2cAddress: Int,
) : I2cDevice {
    companion object {
        private val defaultDelayBetweenWriteAndRead = 1.milliseconds
    }

    init {
        require(i2cAddress in 0..127)
    }

    protected suspend fun doWrite(vararg writeBuffer: Int) = hardware.doWrite(i2cAddress, writeBuffer)

    protected suspend fun doTransaction(
        writeBuffer: IntArray,
        bytesToRead: Int,
        delayBetweenWriteAndRead: Duration = defaultDelayBetweenWriteAndRead,
    ): IntArray {
        return hardware.doTransaction(i2cAddress, writeBuffer, bytesToRead, delayBetweenWriteAndRead)
    }
}


abstract class CustomI2cDevice(
    private val hardware: Hardware,
    private val logger: KLogger,
    final override val i2cAddress: Int,
    private val delayBetweenWriteAndRead: Duration = 1.milliseconds,
) : I2cDevice {

    companion object {

        private const val NUMBER_OF_REPETITIONS = 5

        internal fun calculateCcittCrc8(data: IntArray, range: ClosedRange<Int>): Int {
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

        internal fun createCommandWithCrc(vararg cmd: Int): IntArray {
            val buff = IntArray(cmd.size + 1)
            cmd.copyInto(buff, 1, 0, cmd.size)
            buff[0] = calculateCcittCrc8(buff, 1..cmd.size)
            return buff
        }

        internal fun verifyResponse(reqBuff: IntArray, respBuff: IntArray) {
            try {
                require(respBuff.size >= 2) { "response has to be at least 2 bytes" }

                val received = respBuff.first()
                val calculatedLocally = calculateCcittCrc8(respBuff, 1..<respBuff.size)

                check(calculatedLocally == received) { "CRC mismatch, calculated CRC8 = $calculatedLocally, received = $received" }
                check(reqBuff[1] == respBuff[1]) { "invalid response, request number was ${reqBuff[1]}, got ${respBuff[1]}" }
            } catch (e: Exception) {
                error("problem with following request: ${reqBuff.contentToString()}, response: ${respBuff.contentToString()}: ${e.message}")
            }
        }
    }

    suspend fun sendCommand(
        operationDescription: String,
        vararg cmd: Int,
    ) {
        val request = createCommandWithCrc(*cmd)

        return trySeveralTimes(NUMBER_OF_REPETITIONS, logger, operationDescription) {
            val returned = hardware.doTransaction(i2cAddress, request, 2, delayBetweenWriteAndRead)

            check(returned.size == 2)
            verifyResponse(request, returned)
        }
    }

    suspend fun sendCommandAndReadData(
        operationDescription: String,
        noBytesToRead: Int,
        vararg cmd: Int,
    ): IntArray {
        require(noBytesToRead in 0..20) { "invalid value for number of bytes to read" }
        val commandWithCrc = createCommandWithCrc(*cmd)

        return trySeveralTimes(NUMBER_OF_REPETITIONS, logger, operationDescription) {
            val readBuffer = hardware.doTransaction(i2cAddress, commandWithCrc, noBytesToRead, delayBetweenWriteAndRead)
            verifyResponse(commandWithCrc, readBuffer)
            readBuffer
        }
    }
}