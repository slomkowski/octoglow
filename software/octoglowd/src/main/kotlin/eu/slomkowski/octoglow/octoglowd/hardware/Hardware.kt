package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.contentToString
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import io.dvlopt.linux.i2c.I2CFunctionality
import io.dvlopt.linux.i2c.I2CTransaction
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface Hardware : AutoCloseable {

    val clockDisplay: ClockDisplay
    val frontDisplay: FrontDisplay
    val geiger: Geiger
    val dac: Dac
    val scd40: Scd40
    val bme280: Bme280

    override fun close()

    suspend fun setBrightness(brightness: Int)

    suspend fun doWrite(i2cAddress: Int, writeBuffer: I2CBuffer)

    suspend fun doTransaction(
        i2cAddress: Int,
        writeBuffer: I2CBuffer,
        bytesToRead: Int,
        delayBetweenWriteAndRead: Duration,
    ): I2CBuffer
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

    @Volatile
    private var lastHardwareCallEnded: Instant = Clock.System.now()

    companion object {
        private val logger = KotlinLogging.logger {}

        private val minWaitTimeBetweenSubsequentCalls = 2.milliseconds

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

    override val frontDisplay = FrontDisplayReal(this) // todo change with mock in HardwareMock

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

    val brightnessDevices = allDevices.filterIsInstance<HasBrightness>()

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

    override suspend fun doWrite(i2cAddress: Int, writeBuffer: I2CBuffer) = withContext(Dispatchers.IO) {
        try {
            executeExclusivelyAndWaitIfRequired {
                bus.doTransaction(I2CTransaction(1).apply {
                    getMessage(0).apply {
                        address = i2cAddress
                        buffer = writeBuffer
                    }
                })
            }
        } catch (e: Exception) {
            throw handleI2cException(e)
        }
    }

    private suspend fun executeExclusivelyAndWaitIfRequired(executionBlock: suspend () -> Unit) {

        busMutex.withLock {
            val durationToWait = (Clock.System.now() - lastHardwareCallEnded).coerceIn(0.milliseconds, minWaitTimeBetweenSubsequentCalls)
            if (durationToWait > 0.milliseconds) {
                delay(durationToWait)
            }

            executionBlock()

            lastHardwareCallEnded = Clock.System.now()
        }
    }

    override suspend fun doTransaction(
        i2cAddress: Int,
        writeBuffer: I2CBuffer,
        bytesToRead: Int,
        delayBetweenWriteAndRead: Duration,
    ): I2CBuffer {
        require(bytesToRead in 1..100)
        val numberOfTries = 3

        val readBuffer = I2CBuffer(bytesToRead)

        withContext(Dispatchers.IO) {
            for (tryNo in 1..numberOfTries) {
                try {
                    executeExclusivelyAndWaitIfRequired {
                        bus.selectSlave(i2cAddress)
                        bus.write(writeBuffer)
                        delay(delayBetweenWriteAndRead)
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
                        logger.warn { "errno 6 happened, retrying ($tryNo/$numberOfTries)." }
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