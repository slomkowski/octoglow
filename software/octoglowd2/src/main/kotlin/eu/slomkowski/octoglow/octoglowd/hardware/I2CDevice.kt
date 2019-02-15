package eu.slomkowski.octoglow.octoglowd.hardware

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

    protected fun I2CBuffer.toByteArray(): ByteArray = (0 until length).map { get(it).toByte() }.toByteArray()

    protected fun I2CBuffer.set(index: Int, v: Byte): I2CBuffer = this.set(index, v.toInt())

    private fun List<Int>.toI2CBuffer(): I2CBuffer {
        val buffer = I2CBuffer(this.size)
        this.forEachIndexed { idx, v -> buffer.set(idx, v) }
        return buffer
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