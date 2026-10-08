package eu.slomkowski.octoglow.octoglowd.firmware

import eu.slomkowski.octoglow.octoglowd.hardware.ClockDisplay
import eu.slomkowski.octoglow.octoglowd.hardware.FrontDisplayReal
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Twiboot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Devices with the twiboot I2C bootloader.
 */
enum class FirmwareTarget(
    /**
     * Same as the directory of the device's firmware in the repository, firmware/<name>.
     */
    val commandLineName: String,
    val bootloaderI2cAddress: Int,
    val signature: Int,
) {
    CLOCK_DISPLAY("clock-display", ClockDisplay.BOOTLOADER_I2C_ADDRESS, 0x1e9208), // ATtiny461A
    FRONT_DISPLAY("front-display", FrontDisplayReal.BOOTLOADER_I2C_ADDRESS, 0x1e930f), // ATmega88P
    ;

    suspend fun enterBootloader(hardware: Hardware) = when (this) {
        CLOCK_DISPLAY -> hardware.clockDisplay.enterBootloader()
        FRONT_DISPLAY -> hardware.frontDisplay.enterBootloader()
    }

    /**
     * Throws if the application doesn't respond properly.
     */
    suspend fun checkApplication(hardware: Hardware) {
        when (this) {
            CLOCK_DISPLAY -> hardware.clockDisplay.retrieveRelaysState()
            FRONT_DISPLAY -> hardware.frontDisplay.getEndOfConstructionYearInternal()
        }
    }

    companion object {
        fun fromCommandLineName(name: String): FirmwareTarget? = entries.firstOrNull { it.commandLineName == name }
    }
}

class FirmwareBurnException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Uploads the firmware over I2C: resets the device into the bootloader, writes and verifies
 * page by page, then starts the new application.
 */
class FirmwareBurner(
    private val hardware: Hardware,
    private val target: FirmwareTarget,
    private val bootloaderConnectTimeout: Duration = 10.seconds,
    private val onProgress: suspend (writtenPages: Int, totalPages: Int) -> Unit = { _, _ -> },
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        private val pollInterval = 20.milliseconds
        private val pageWriteTime = 10.milliseconds // erase + write of one page, the bootloader also stretches the clock
        private val applicationStartTime = 300.milliseconds
        private const val PAGE_WRITE_ATTEMPTS = 3
    }

    private val twiboot = Twiboot(hardware, target.bootloaderI2cAddress)

    suspend fun burn(image: FirmwareImage) {
        enterBootloader()

        val version = twiboot.readVersion()
        val chipInfo = twiboot.readChipInfo()
        logger.info { "Connected to bootloader '$version', $chipInfo." }

        if (chipInfo.signature != target.signature) {
            throw FirmwareBurnException("unexpected signature 0x${chipInfo.signature.toString(16)}, expected 0x${target.signature.toString(16)}")
        }
        if (chipInfo.pageSize !in 2..Twiboot.MAX_READ_LENGTH) {
            throw FirmwareBurnException("unsupported page size ${chipInfo.pageSize}")
        }
        if (image.endAddress > chipInfo.applicationFlashSize) {
            throw FirmwareBurnException(
                "image ends at 0x${image.endAddress.toString(16)}, application flash is only ${chipInfo.applicationFlashSize} bytes; " +
                        "is it the application without the bootloader?"
            )
        }

        val totalPages = (image.endAddress + chipInfo.pageSize - 1) / chipInfo.pageSize
        logger.info { "Writing ${image.endAddress} bytes in $totalPages pages." }

        for (page in 0 until totalPages) {
            val address = page * chipInfo.pageSize
            writeAndVerifyPage(address, image.slice(address, chipInfo.pageSize))
            onProgress(page + 1, totalPages)
        }

        logger.info { "Firmware written and verified, starting the application." }
        twiboot.startApplication()
        delay(applicationStartTime)

        try {
            target.checkApplication(hardware)
        } catch (e: Exception) {
            throw FirmwareBurnException("firmware written, but the application doesn't respond", e)
        }

        logger.info { "Application of $target is running." }
    }

    private suspend fun enterBootloader() {
        try {
            target.enterBootloader(hardware)
            logger.info { "$target is resetting into the bootloader." }
        } catch (e: Exception) {
            // it may already be in the bootloader or the application may be broken
            logger.warn { "$target didn't accept the enter bootloader command: ${e.message}." }
        }

        var firstAttempt = true
        withTimeoutOrNull(bootloaderConnectTimeout) {
            while (true) {
                try {
                    twiboot.abortBootTimeout()
                    return@withTimeoutOrNull
                } catch (_: IOException) {
                    if (firstAttempt) {
                        logger.info { "Waiting for the bootloader at 0x${target.bootloaderI2cAddress.toString(16)}; if the application is broken, power-cycle the device now." }
                        firstAttempt = false
                    }
                    delay(pollInterval)
                }
            }
        } ?: throw FirmwareBurnException("bootloader didn't respond within $bootloaderConnectTimeout")
    }

    private suspend fun writeAndVerifyPage(address: Int, data: IntArray) {
        var lastProblem: Exception? = null

        repeat(PAGE_WRITE_ATTEMPTS) { attempt ->
            twiboot.writeFlashPage(address, data)
            delay(pageWriteTime)

            try {
                val readBack = twiboot.readFlash(address, data.size)
                if (readBack.contentEquals(data)) {
                    return
                }
                lastProblem = FirmwareBurnException("verification failed")
                logger.warn { "Page 0x${address.toString(16)} verification failed (attempt ${attempt + 1}/$PAGE_WRITE_ATTEMPTS)." }
            } catch (e: IOException) {
                lastProblem = e
                logger.warn { "Page 0x${address.toString(16)} read back failed (attempt ${attempt + 1}/$PAGE_WRITE_ATTEMPTS): ${e.message}." }
            }
        }

        throw FirmwareBurnException("failed to write page 0x${address.toString(16)}", lastProblem)
    }
}
