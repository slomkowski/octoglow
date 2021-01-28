package eu.slomkowski.octoglow.octoglowd.hardware

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.contentToString
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import io.dvlopt.linux.i2c.I2CFunctionality
import io.dvlopt.linux.i2c.I2CTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KLogging
import java.io.IOException
import java.time.Duration

class Hardware(
    private val bus: I2CBus,
    ringAtStartup: Boolean
) : AutoCloseable {

    companion object : KLogging()

    private val busMutex = Mutex()

    val clockDisplay = ClockDisplay(this)

    val frontDisplay = FrontDisplay(this)

    val geiger = Geiger(this)

    val dac = Dac(this)

    val bme280 = Bme280(this)

    private val allDevices = listOf(clockDisplay, frontDisplay, geiger, dac, bme280)

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

                if (ringAtStartup) {
                    clockDisplay.ringBell(Duration.ofMillis(70))
                }
            }
        } catch (e: Exception) {
            logger.error("Error during hardware initialization;", e)
        }
    }

    constructor(config: Config) : this(I2CBus(config[ConfKey.i2cBus]), config[ConfKey.ringAtStartup])

    suspend fun setBrightness(brightness: Int) {
        listOf<HasBrightness>(clockDisplay, frontDisplay, geiger).forEach { it.setBrightness(brightness) }
    }

    override fun close() {
        allDevices.forEach { it.close() }
    }

    suspend fun doWrite(i2cAddress: Int, writeBuffer: I2CBuffer) = busMutex.withLock {
        bus.doTransaction(I2CTransaction(1).apply {
            getMessage(0).apply {
                address = i2cAddress
                buffer = writeBuffer
            }
        })
    }

    suspend fun doTransaction(
        i2cAddress: Int,
        writeBuffer: I2CBuffer,
        bytesToRead: Int
    ): I2CBuffer {
        require(bytesToRead in 1..100)
        val numberOfTries = 3

        val readBuffer = I2CBuffer(bytesToRead)

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
                    throw e
                }
            }
        }

        return readBuffer
    }
}