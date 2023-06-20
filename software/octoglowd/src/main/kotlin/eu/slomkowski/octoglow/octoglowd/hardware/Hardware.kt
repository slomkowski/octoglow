package eu.slomkowski.octoglow.octoglowd.hardware

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.contentToString
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import io.dvlopt.linux.i2c.I2CFunctionality
import io.dvlopt.linux.i2c.I2CTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mu.KLogging
import java.io.IOException

class Hardware(
    private val bus: I2CBus,
) : AutoCloseable {

    enum class Errno(
        val code: Int,
        val message: String
    ) {
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

    companion object : KLogging() {
        fun handleI2cException(baseException: Exception): Exception =
            Regex("Native error while doing an I2C transaction : errno (\\d+)")
                .matchEntire(baseException.message?.trim() ?: "")
                ?.let { matchResult ->
                    val errno = matchResult.groupValues[1].toInt()

                    val knownErrno = Errno.values().firstOrNull { errno == it.code }

                    IOException(
                        "native I2C transaction error: " + when (knownErrno) {
                            null -> "errno $errno"
                            else -> "$knownErrno: ${knownErrno.message}"
                        }, baseException
                    )
                } ?: baseException
    }


    private val busMutex = Mutex()

    val clockDisplay = ClockDisplay(this)

    val frontDisplay = FrontDisplay(this)

    val geiger = Geiger(this)

    val dac = Dac(this)

    private val allDevices = listOf(clockDisplay, frontDisplay, geiger, dac)

    init {
        require(bus.functionalities.can(I2CFunctionality.TRANSACTIONS)) { "I2C bus requires transaction support" }
        require(bus.functionalities.can(I2CFunctionality.READ_BYTE)) { "I2C requires read byte support" }

        try {
            runBlocking {
                allDevices.forEach { it.initDevice() }

                frontDisplay.apply {
                    clear()
                    setStaticText(0, "Initializing...")
                }
            }
        } catch (e: Exception) {
            logger.error("Error during hardware initialization;", e)
        }
    }

    constructor(config: Config) : this(I2CBus(config[ConfKey.i2cBus]))

    suspend fun setBrightness(brightness: Int) {
        listOf<HasBrightness>(clockDisplay, frontDisplay, geiger).forEach { it.setBrightness(brightness) }
    }

    override fun close() {
        allDevices.forEach { it.close() }
    }

    suspend fun doWrite(i2cAddress: Int, writeBuffer: I2CBuffer) = busMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                bus.doTransaction(I2CTransaction(1).apply {
                    getMessage(0).apply {
                        address = i2cAddress
                        buffer = writeBuffer
                    }
                })
            } catch (e: Exception) {
                throw handleI2cException(e)
            }
        }
    }

    suspend fun doTransaction(
        i2cAddress: Int,
        writeBuffer: I2CBuffer,
        bytesToRead: Int
    ): I2CBuffer {
        require(bytesToRead in 1..100)
        val numberOfTries = 3

        val readBuffer = I2CBuffer(bytesToRead)

        withContext(Dispatchers.IO) {
            for (tryNo in 1..numberOfTries) {
                try {
                    busMutex.withLock {
                        bus.selectSlave(i2cAddress)
                        bus.write(writeBuffer)
                        delay(1)
                        bus.read(readBuffer)
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
                        logger.error(e) { "Error in bus transaction ($tryNo/$numberOfTries)" }
                        throw handleI2cException(e)
                    }
                }
            }
        }

        return readBuffer
    }
}