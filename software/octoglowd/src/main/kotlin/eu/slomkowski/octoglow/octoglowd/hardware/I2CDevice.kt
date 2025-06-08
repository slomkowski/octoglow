package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.I2CBuffer
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime

abstract class I2CDevice @OptIn(ExperimentalTime::class) constructor(
    private val hardware: Hardware,
    private val i2cAddress: Int,
    private val logger: KLogger,
) : AutoCloseable {

    init {
        require(i2cAddress in 0..127)
    }

    open suspend fun initDevice() {}

    open suspend fun closeDevice() {}

    final override fun close() {
        try {
            runBlocking { closeDevice() }
        } catch (e: Exception) {
            logger.error(e) { "Failed to close device $this." }
        }
    }

    suspend fun doWrite(vararg bytes: Int) {
        val buff = I2CBuffer(bytes.size)
        bytes.forEachIndexed { idx, v -> buff.set(idx, v) }
        doWrite(buff)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun doWrite(writeBuffer: I2CBuffer) = hardware.doWrite(i2cAddress, writeBuffer)

    suspend fun doTransaction(command: List<Int>, bytesToRead: Int): I2CBuffer {
        return doTransaction(command.toI2CBuffer(), bytesToRead)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun doTransaction(writeBuffer: I2CBuffer, bytesToRead: Int) =
        hardware.doTransaction(i2cAddress, writeBuffer, bytesToRead)
}