package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.Config
import io.github.oshai.kotlinlogging.KotlinLogging
import io.helins.linux.i2c.I2CBuffer
import io.helins.linux.i2c.I2CBus
import io.helins.linux.i2c.I2CFunctionality
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

interface Hardware : AutoCloseable {

    val clockDisplay: ClockDisplay
    val frontDisplay: FrontDisplay
    val geiger: Geiger
    val dac: Dac
    val scd40: Scd40
    val bme280: Bme280

    override fun close()

    suspend fun setBrightness(brightness: Int)

    suspend fun doWrite(i2cAddress: Int, writeData: IntArray)

    suspend fun doTransaction(
        i2cAddress: Int,
        writeData: IntArray,
        bytesToRead: Int,
        delayBetweenWriteAndRead: Duration,
    ): IntArray
}

@ExperimentalTime
class HardwareReal(
    private val bus: I2CBus,
) : Hardware {

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

    private val busMutex = Mutex()
    private val writeI2cBuffer = I2CBuffer(I2C_BUFFER_MAX_SIZE)
    private val readI2cBuffer = I2CBuffer(I2C_BUFFER_MAX_SIZE)

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val I2C_BUFFER_MAX_SIZE = 500

        private val exceptionRegex = Regex("errno (\\d+)")

        fun handleI2cException(baseException: Exception): Exception = exceptionRegex
            .find(baseException.message ?: "")
            ?.let { matchResult ->
                val errno = matchResult.groupValues[1].toInt()

                val knownErrno = Errno.entries.firstOrNull { errno == it.code }

                IOException(
                    "native I2C transaction error: " + when (knownErrno) {
                        null -> "errno $errno"
                        else -> "$knownErrno: ${knownErrno.message}"
                    }, baseException
                )
            } ?: baseException
    }

    override val clockDisplay = ClockDisplay(this)

    override val frontDisplay = FrontDisplayReal(this)

    override val geiger = Geiger(this)

    override val dac = Dac(this)

    override val scd40 = Scd40(this)

    override val bme280 = Bme280(this)

    private val allDevices = listOf(
        frontDisplay,
        clockDisplay,
        geiger,
        dac,
        scd40,
        bme280,
    )

    private val brightnessDevices = allDevices.filterIsInstance<HasBrightness>()

    init {
        require(bus.functionalities.can(I2CFunctionality.TRANSACTIONS)) { "I2C bus requires transaction support" }
        require(bus.functionalities.can(I2CFunctionality.READ_BYTE)) { "I2C requires read byte support" }

        try {
            runBlocking {
                frontDisplay.apply {
                    initDevice()
                    clear()
                    setStaticText(0, "Initializing...")
                }

                allDevices.minus(frontDisplay).forEach { launch { it.initDevice() } }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error during hardware initialization;" }
        }
    }

    constructor(config: Config) : this(I2CBus(config.i2cBus))

    override suspend fun setBrightness(brightness: Int): Unit = coroutineScope {
        brightnessDevices.forEach { launch { it.setBrightness(brightness) } }
    }

    override fun close() {
        runBlocking {
            for (device in allDevices) {
                try {
                    device.closeDevice()
                } catch (e: Exception) {
                    logger.error(e) { "Failed to close device $this." }
                }
            }
        }
    }

    private fun fillWriteBuffer(writeArray: IntArray) {
        require(writeArray.isNotEmpty()) { "array with data do write cannot be empty" }
        require(writeArray.size <= writeI2cBuffer.length) { "write array size ${writeArray.size} exceeds buffer length ${writeI2cBuffer.length}" }
        writeArray.forEachIndexed { index, value -> writeI2cBuffer[index] = value }
    }

    override suspend fun doWrite(i2cAddress: Int, writeData: IntArray) = withContext(Dispatchers.IO) {
        try {
            busMutex.withLock {
                fillWriteBuffer(writeData)
                bus.selectSlave(i2cAddress)
                bus.write(writeI2cBuffer, writeData.size)
            }
        } catch (e: Exception) {
            throw handleI2cException(e)
        }
    }

    override suspend fun doTransaction(
        i2cAddress: Int,
        writeData: IntArray,
        bytesToRead: Int,
        delayBetweenWriteAndRead: Duration,
    ): IntArray {
        require(bytesToRead in 1..100)
        val numberOfTries = 3

        val resultArray = IntArray(bytesToRead)

        withContext(Dispatchers.IO) {
            for (tryNo in 1..numberOfTries) {
                try {
                    busMutex.withLock {
                        fillWriteBuffer(writeData)
                        bus.selectSlave(i2cAddress)
                        bus.write(writeI2cBuffer, writeData.size)
                        delay(delayBetweenWriteAndRead)
                        bus.read(readI2cBuffer, bytesToRead)

                        for (i in 0 until bytesToRead) {
                            resultArray[i] = readI2cBuffer.get(i)
                        }
                    }

                    break
                } catch (e: IOException) {
                    logger.debug {
                        "doTransaction error. " +
                                "Address 0x${i2cAddress.toString(16)}. " +
                                "Write buffer: ${writeData.contentToString()}, " +
                                "read buffer: ${resultArray.contentToString()}."
                    }

                    if (e.message?.contains("errno 6", ignoreCase = true) == true && tryNo < numberOfTries) {
                        logger.warn { "errno 6 happened, retrying ($tryNo/$numberOfTries)." }
                        continue
                    } else {
                        logger.error(e) { "Error in bus transaction ($tryNo/$numberOfTries)" }
                        throw handleI2cException(e)
                    }
                }
            }
        }

        return resultArray
    }
}