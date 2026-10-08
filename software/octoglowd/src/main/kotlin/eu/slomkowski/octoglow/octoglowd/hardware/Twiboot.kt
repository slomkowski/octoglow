package eu.slomkowski.octoglow.octoglowd.hardware

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import kotlin.time.Duration

data class TwibootChipInfo(
    val signature: Int,
    val pageSize: Int,
    val applicationFlashSize: Int,
    val eepromSize: Int,
)

/**
 * Host side of the twiboot I2C bootloader protocol (https://github.com/orempel/twiboot),
 * the device side is in firmware/clock-display/bootloader/protocol.hpp and firmware/front-display/bootloader/twiboot.hpp.
 *
 * The bootloader keeps the current command across the stop condition, so each command is
 * a separate write, optionally followed by a separate read, like the twiboot tool does.
 */
class Twiboot(
    private val hardware: Hardware,
    val i2cAddress: Int,
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        private const val CMD_WAIT = 0x00
        private const val CMD_READ_VERSION = 0x01
        private const val CMD_SWITCH_APPLICATION = 0x01
        private const val CMD_ACCESS_MEMORY = 0x02

        private const val BOOTTYPE_APPLICATION = 0x80

        private const val MEMTYPE_CHIPINFO = 0x00
        private const val MEMTYPE_FLASH = 0x01

        private const val VERSION_LENGTH = 16
        private const val CHIP_INFO_LENGTH = 8

        /**
         * Limit of [Hardware.doTransaction].
         */
        const val MAX_READ_LENGTH = 100
    }

    init {
        require(i2cAddress in 0..127)
    }

    /**
     * Stops the boot countdown, the bootloader then waits for commands until [startApplication].
     * Throws [IOException] if the bootloader doesn't respond, so it can be used for polling.
     */
    suspend fun abortBootTimeout() = hardware.doWrite(i2cAddress, intArrayOf(CMD_WAIT))

    suspend fun readVersion(): String = hardware
        .doTransaction(i2cAddress, intArrayOf(CMD_READ_VERSION), VERSION_LENGTH, Duration.ZERO)
        .map { (it and 0x7f).toChar() }
        .joinToString("")
        .trimEnd('\u0000')

    suspend fun readChipInfo(): TwibootChipInfo {
        val r = hardware.doTransaction(i2cAddress, memoryCommand(MEMTYPE_CHIPINFO, 0), CHIP_INFO_LENGTH, Duration.ZERO)
        return TwibootChipInfo(
            signature = (r[0] shl 16) or (r[1] shl 8) or r[2],
            pageSize = r[3],
            applicationFlashSize = (r[4] shl 8) or r[5],
            eepromSize = (r[6] shl 8) or r[7],
        )
    }

    /**
     * The bootloader returns the original (unpatched) vector table, as sent by [writeFlashPage].
     */
    suspend fun readFlash(address: Int, length: Int): IntArray {
        require(length in 1..MAX_READ_LENGTH) { "invalid read length $length" }
        return hardware.doTransaction(i2cAddress, memoryCommand(MEMTYPE_FLASH, address), length, Duration.ZERO)
    }

    /**
     * The USI variant of twiboot (clock display) sends NAK for the last byte of the page, after the page is written
     * (the clock is stretched meanwhile). Most Linux I2C drivers report it as an I/O error, so the error
     * is ignored here: the page has to be verified with [readFlash]. The TWI variant (front display) ACKs it.
     */
    suspend fun writeFlashPage(address: Int, data: IntArray) {
        try {
            hardware.doWrite(i2cAddress, memoryCommand(MEMTYPE_FLASH, address) + data)
        } catch (e: IOException) {
            logger.debug { "Writing page 0x${address.toString(16)} ended with '${e.message}', expected NAK on the last byte." }
        }
    }

    /**
     * Like [writeFlashPage], the last byte is NAKed, so the error is ignored.
     */
    suspend fun startApplication() {
        try {
            hardware.doWrite(i2cAddress, intArrayOf(CMD_SWITCH_APPLICATION, BOOTTYPE_APPLICATION))
        } catch (e: IOException) {
            logger.debug { "Start application command ended with '${e.message}', expected NAK on the last byte." }
        }
    }

    private fun memoryCommand(memoryType: Int, address: Int): IntArray {
        require(address in 0..0xffff)
        return intArrayOf(CMD_ACCESS_MEMORY, memoryType, (address shr 8) and 0xff, address and 0xff)
    }
}
