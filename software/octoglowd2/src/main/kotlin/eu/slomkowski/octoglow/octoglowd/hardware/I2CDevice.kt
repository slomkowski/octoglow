package eu.slomkowski.octoglow.octoglowd.hardware

import io.dvlopt.linux.i2c.*
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

abstract class I2CDevice(
        private val threadContext: CoroutineContext,
        private val i2c: I2CBus,
        private val i2cAddress: Int) {

    init {
        require(i2cAddress in 0..127)
        require(i2c.functionalities.can(I2CFunctionality.TRANSACTIONS)) { "I2C bus requires transaction support" }
    }

    protected fun I2CBuffer.set(index: Int, v: Byte): I2CBuffer = this.set(index, v.toInt())

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
        val writeBuffer = I2CBuffer(command.size)
        command.forEachIndexed { idx, v -> writeBuffer.set(idx, v) }
        return doTransaction(writeBuffer, bytesToRead)
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