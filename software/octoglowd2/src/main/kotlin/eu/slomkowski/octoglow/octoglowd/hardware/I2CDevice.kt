package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.*
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

abstract class I2CDevice(
        protected val threadContext: CoroutineContext,
        protected val i2c: I2CBus,
        protected val i2cAddress: Int) : AutoCloseable {

    init {
        require(i2cAddress in 0..127)
        require(i2c.functionalities.can(I2CFunctionality.TRANSACTIONS)) { "I2C bus requires transaction support" }
    }

    suspend fun doWrite(vararg bytes: Int) {
        val buff = I2CBuffer(bytes.size)
        bytes.forEachIndexed { idx, v -> buff.set(idx, v) }
        doWrite(buff)
    }

    suspend fun doWrite(writeBuffer: I2CBuffer) = withContext(threadContext) {
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

    suspend fun doTransaction(writeBuffer: I2CBuffer, bytesToRead: Int): I2CBuffer = withContext(threadContext) {
        require(bytesToRead in 1..100)

        val readBuffer = I2CBuffer(bytesToRead)
        i2c.doTransaction(I2CTransaction(2).apply {
            getMessage(0).apply {
                address = i2cAddress
                buffer = writeBuffer
            }
            getMessage(1).apply {
                address = i2cAddress
                buffer = readBuffer
                flags = I2CFlags().set(I2CFlag.READ)
            }
        })
        readBuffer
    }
}