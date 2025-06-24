package eu.slomkowski.octoglow.octoglowd.hardware

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

    suspend fun doTransaction(command: IntArray,
                              bytesToRead: Int,
                              delayBetweenWriteAndRead : Duration = defaultDelayBetweenWriteAndRead,
                              ): I2CBuffer {
        return doTransaction(command.toI2CBuffer(), bytesToRead,delayBetweenWriteAndRead)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun doTransaction(writeBuffer: I2CBuffer,
                              bytesToRead: Int,
                              delayBetweenWriteAndRead : Duration = defaultDelayBetweenWriteAndRead,
                              ): I2CBuffer =
        hardware.doTransaction(i2cAddress, writeBuffer, bytesToRead,  delayBetweenWriteAndRead)
}