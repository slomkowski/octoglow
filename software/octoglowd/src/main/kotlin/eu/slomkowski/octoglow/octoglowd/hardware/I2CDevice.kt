package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.I2CBuffer
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
abstract class I2CDevice(
    private val hardware: Hardware,
    private val i2cAddress: Int,
    private val logger: KLogger,
) {

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

    suspend fun doTransaction(command: IntArray, bytesToRead: Int): I2CBuffer {
        return doTransaction(command.toI2CBuffer(), bytesToRead)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun doTransaction(writeBuffer: I2CBuffer, bytesToRead: Int): I2CBuffer =
        hardware.doTransaction(i2cAddress, writeBuffer, bytesToRead)
}