package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.I2CBuffer
import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime

@ExperimentalTime
abstract class I2CDevice(
    private val hardware: Hardware,
    private val i2cAddress: Int
) : AutoCloseable {

    init {
        require(i2cAddress in 0..127)
    }

    open suspend fun initDevice() {} //todo

    open suspend fun closeDevice() {} //todo

    final override fun close() {
        try {
            runBlocking { closeDevice() }
        } catch (e: Exception) {
            //todo
        }
    }

    suspend fun doWrite(vararg bytes: Int) {
        val buff = I2CBuffer(bytes.size)
        bytes.forEachIndexed { idx, v -> buff.set(idx, v) }
        doWrite(buff)
    }

    suspend fun doWrite(writeBuffer: I2CBuffer) = hardware.doWrite(i2cAddress, writeBuffer)

    suspend fun doTransaction(command: List<Int>, bytesToRead: Int): I2CBuffer {
        return doTransaction(command.toI2CBuffer(), bytesToRead)
    }

    suspend fun doTransaction(writeBuffer: I2CBuffer, bytesToRead: Int) =
        hardware.doTransaction(i2cAddress, writeBuffer, bytesToRead)
}