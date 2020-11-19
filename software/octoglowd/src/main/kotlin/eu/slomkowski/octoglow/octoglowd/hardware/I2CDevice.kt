package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToString
import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import io.dvlopt.linux.i2c.I2CFunctionality
import io.dvlopt.linux.i2c.I2CTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KLogging
import java.io.IOException

abstract class I2CDevice(
        private val i2cMutex: Mutex,
        private val i2c: I2CBus,
        private val i2cAddress: Int) : AutoCloseable {

    companion object : KLogging()

    init {
        require(i2cAddress in 0..127)
        require(i2c.functionalities.can(I2CFunctionality.TRANSACTIONS)) { "I2C bus requires transaction support" }
        require(i2c.functionalities.can(I2CFunctionality.READ_BYTE)) { "I2C requires read byte support" }
    }

    suspend fun doWrite(vararg bytes: Int) {
        val buff = I2CBuffer(bytes.size)
        bytes.forEachIndexed { idx, v -> buff.set(idx, v) }
        doWrite(buff)
    }

    suspend fun doWrite(writeBuffer: I2CBuffer) = i2cMutex.withLock {
        i2c.doTransaction(I2CTransaction(1).apply {
            getMessage(0).apply {
                address = i2cAddress
                buffer = writeBuffer
            }
        })
    }

    suspend fun doTransaction(command: List<Int>, bytesToRead: Int): I2CBuffer {
        return doTransaction(command.toI2CBuffer(), bytesToRead)
    }

    suspend fun doTransaction(writeBuffer: I2CBuffer, bytesToRead: Int): I2CBuffer {
        require(bytesToRead in 1..100)
        val numberOfTries = 3

        val readBuffer = I2CBuffer(bytesToRead)

        for (tryNo in 1..numberOfTries) {
            try {
                i2cMutex.withLock {
                    i2c.selectSlave(i2cAddress)
                    i2c.write(writeBuffer)
                    delay(1)
                    i2c.read(readBuffer)
                }

                break
            } catch (e: IOException) {
                logger.debug {
                    "doTransaction error. " +
                            "Address 0x${i2cAddress.toString(16)}. " +
                            "Write buffer: ${writeBuffer.contentToString()}, " +
                            "read buffer: ${readBuffer.contentToString()};"
                }

                if (e.message?.contains("errno 6", ignoreCase = true) == true && tryNo < numberOfTries) {
                    logger.warn("errno 6 happened, retrying ({}/{}).", tryNo, numberOfTries)
                    continue
                } else {
                    logger.error(e) { "Error in I2C transaction ($tryNo/$numberOfTries)" }
                    throw e
                }
            }
        }

        return readBuffer
    }
}