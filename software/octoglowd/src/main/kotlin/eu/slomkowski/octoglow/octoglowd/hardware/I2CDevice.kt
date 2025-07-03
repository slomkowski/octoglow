package eu.slomkowski.octoglow.octoglowd.hardware

import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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

        fun createCommandWithCrc(vararg cmd: Int): IntArray {
            val buff = IntArray(cmd.size + 1) { 0 }
            cmd.copyInto(buff, 1, 0, cmd.size)
            buff[0] = calculateCcittCrc8(cmd, cmd.indices)
            return buff
        }

        fun verifyResponse(reqBuff: IntArray, respBuff: IntArray) {
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

    init {
        require(i2cAddress in 0..127)
    }

    abstract suspend fun initDevice()

    abstract suspend fun closeDevice()

    suspend fun doWrite(vararg writeBuffer: Int) = hardware.doWrite(i2cAddress, writeBuffer)

    suspend fun doTransaction(
        writeBuffer: IntArray,
        bytesToRead: Int,
        delayBetweenWriteAndRead: Duration = defaultDelayBetweenWriteAndRead,
    ): IntArray {
        return hardware.doTransaction(i2cAddress, writeBuffer, bytesToRead, delayBetweenWriteAndRead)
    }

    // move to class like CustomI2cDevice?
    suspend fun sendCommand(vararg cmd: Int) {
        val request = createCommandWithCrc(*cmd)
        val returned = doTransaction(request, 2)
        check(returned.size == 2)
        verifyResponse(request, returned)
    }
}