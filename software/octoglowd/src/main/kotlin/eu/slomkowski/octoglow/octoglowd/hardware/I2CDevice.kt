package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.*
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.CoroutineContext

abstract class I2CDevice(
        protected val threadContext: CoroutineContext,
        protected val i2c: I2CBus,
        private val i2cAddress: Int) : AutoCloseable {

    enum class Errno(
            val code: Int,
            val message: String) {
        EAGAIN(11, "Resource temporarily unavailable  (same code as EWOULDBLOCK)"),
        EBADMSG(74, "Bad message"),
        EBUSY(16, "Device or resource busy"),
        EIN(22, "Invalid argument"),
        EIO(5, "Input/output error"),
        ENODEV(19, "No such device"),
        ENOMEM(12, "Cannot allocate memory"),
        ENXIO(6, "No such device or address"),
        EOPNOTSUPP(95, "Operation not supported"),
        EPROTO(71, "Protocol error"),
        ESHUTDOWN(108, "Cannot send after transport endpoint shutdown"),
        ETIMEDOUT(110, "Connection timed out")
    }

    companion object {
        fun handleI2cException(baseException: Exception): Exception = Regex("Native error while doing an I2C transaction : errno (\\d+)")
                .matchEntire(baseException.message?.trim() ?: "")
                ?.let { matchResult ->
                    val errno = matchResult.groupValues[1].toInt()

                    val knownErrno = Errno.values().firstOrNull { errno == it.code }

                    IOException("native I2C transaction error: " + when (knownErrno) {
                        null -> "errno $errno"
                        else -> "$knownErrno: ${knownErrno.message}"
                    }, baseException)
                } ?: baseException
    }

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
        try {
            i2c.doTransaction(I2CTransaction(1).apply {
                getMessage(0).apply {
                    address = i2cAddress
                    buffer = writeBuffer
                }
            })
        } catch (e: Exception) {
            throw handleI2cException(e)
        }
    }

    suspend fun doTransaction(command: List<Int>, bytesToRead: Int): I2CBuffer {
        return doTransaction(command.toI2CBuffer(), bytesToRead)
    }

    suspend fun doTransaction(writeBuffer: I2CBuffer, bytesToRead: Int): I2CBuffer = withContext(threadContext) {
        require(bytesToRead in 1..100)

        val readBuffer = I2CBuffer(bytesToRead)
        try {
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
        } catch (e: Exception) {
            throw handleI2cException(e)
        }
        readBuffer
    }
}