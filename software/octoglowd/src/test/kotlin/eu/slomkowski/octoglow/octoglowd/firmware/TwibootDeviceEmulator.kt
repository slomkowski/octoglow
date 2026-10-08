package eu.slomkowski.octoglow.octoglowd.firmware

import eu.slomkowski.octoglow.octoglowd.hardware.*
import eu.slomkowski.octoglow.octoglowd.hardware.CustomI2cDevice.Companion.calculateCcittCrc8
import io.mockk.mockk
import java.io.IOException
import kotlin.time.Duration

/**
 * Clock display with the bootloader from firmware/clock-display/bootloader, on the I2C bus level:
 * the application handles ENTER_BOOTLOADER and GET_RELAY_STATE, the bootloader follows protocol.cpp,
 * including the vector table patching and the NAK on the last byte of a page.
 */
class TwibootDeviceEmulator(
    private val bootloaderStart: Int = 0x0c40,
    private val pageSize: Int = 64,
    private val signature: Int = 0x1e9208,
) : Hardware {

    companion object {
        const val APP_ADDRESS = 0x10
        const val BOOTLOADER_ADDRESS = 0x11
        const val APP_VECTOR_NUM = 9
        const val FLASH_SIZE = 4096

        private const val CMD_WAIT = 0x00
        private const val CMD_SWITCH_APPLICATION = 0x01
        private const val CMD_ACCESS_MEMORY = 0x02
        private const val CMD_ACCESS_CHIPINFO = 0x12
        private const val CMD_ACCESS_FLASH = 0x22
        private const val CMD_BOOT_APPLICATION = 0x21

        fun rjmp(fromWord: Int, toWord: Int) = 0xc000 or ((toWord - fromWord - 1) and 0x0fff)

        private fun nak(): Nothing = throw IOException("native I2C transaction error: errno 121")
        private fun noDevice(): Nothing = throw IOException("native I2C transaction error: errno 6")
    }

    enum class Mode { APPLICATION, BOOTLOADER, DEAD }

    var mode = Mode.APPLICATION

    val flash = IntArray(FLASH_SIZE) { 0xff }

    /**
     * Page addresses which are written incorrectly once, to test the verification.
     */
    val pagesToCorruptOnce = mutableSetOf<Int>()

    var pageWrites = 0
        private set

    // bootloader state, like in protocol.cpp
    private var command = CMD_WAIT
    private var address = 0
    private val pageBuffer = IntArray(pageSize)
    private var savedResetVector = 0
    private var savedAppVector = 0

    override val clockDisplay = ClockDisplay(this)
    override val frontDisplay = mockk<FrontDisplay>(relaxed = true)
    override val geiger = mockk<Geiger>(relaxed = true)
    override val dac = mockk<Dac>(relaxed = true)
    override val scd40 = mockk<Scd40>(relaxed = true)
    override val bme280 = mockk<Bme280>(relaxed = true)

    fun flashWord(byteAddress: Int) = flash[byteAddress] or (flash[byteAddress + 1] shl 8)

    /**
     * Simulates a reset: the patched reset vector always starts the bootloader.
     */
    private fun resetIntoBootloader() {
        mode = Mode.BOOTLOADER
        command = CMD_WAIT
        savedResetVector = flashWord(0)
        savedAppVector = flashWord(APP_VECTOR_NUM * 2)
    }

    override fun close() {}

    override suspend fun setBrightness(brightness: Int) {}

    override suspend fun doWrite(i2cAddress: Int, writeData: IntArray) {
        when {
            i2cAddress == BOOTLOADER_ADDRESS && mode == Mode.BOOTLOADER -> bootloaderWrite(writeData)
            else -> noDevice()
        }
    }

    override suspend fun doTransaction(i2cAddress: Int, writeData: IntArray, bytesToRead: Int, delayBetweenWriteAndRead: Duration): IntArray {
        return when {
            i2cAddress == APP_ADDRESS && mode == Mode.APPLICATION -> applicationTransaction(writeData, bytesToRead)
            i2cAddress == BOOTLOADER_ADDRESS && mode == Mode.BOOTLOADER -> {
                bootloaderWrite(writeData)
                IntArray(bytesToRead) { bootloaderRead(it) }
            }

            else -> noDevice()
        }
    }

    private fun applicationTransaction(writeData: IntArray, bytesToRead: Int): IntArray {
        check(calculateCcittCrc8(writeData, 1..<writeData.size) == writeData[0]) { "invalid CRC" }

        val reply = when (writeData[1]) {
            7 -> {
                check(writeData.sliceArray(2..5).contentEquals("BOOT".map { it.code }.toIntArray()))
                resetIntoBootloader()
                intArrayOf(0, 7)
            }

            6 -> intArrayOf(0, 6, 0)
            else -> error("command ${writeData[1]} not emulated")
        }
        check(reply.size == bytesToRead)
        reply[0] = calculateCcittCrc8(reply, 1..<reply.size)
        return reply
    }

    private fun bootloaderWrite(data: IntArray) {
        data.forEachIndexed { byteNumber, byte ->
            val ack = onDataWrite(byteNumber, byte)
            if (!ack) {
                // the USI slave sends NAK for this byte, Linux reports an error
                nak()
            }
        }
    }

    private fun onDataWrite(byteNumber: Int, data: Int): Boolean = when (byteNumber) {
        0 -> if (data == CMD_WAIT || data == CMD_SWITCH_APPLICATION || data == CMD_ACCESS_MEMORY) {
            command = data
            true
        } else {
            command = CMD_BOOT_APPLICATION
            startApplicationIfRequested()
            false
        }

        1 -> when {
            command == CMD_SWITCH_APPLICATION -> {
                if (data == 0x80) {
                    command = CMD_BOOT_APPLICATION
                    startApplicationIfRequested()
                }
                false
            }

            command == CMD_ACCESS_MEMORY && data == 0x00 -> {
                command = CMD_ACCESS_CHIPINFO
                true
            }

            command == CMD_ACCESS_MEMORY && data == 0x01 -> {
                command = CMD_ACCESS_FLASH
                true
            }

            else -> false
        }

        2, 3 -> {
            address = ((address shl 8) or data) and 0xffff
            true
        }

        else -> if (command != CMD_ACCESS_FLASH) {
            false
        } else {
            val position = byteNumber - 4
            pageBuffer[position] = data
            if (position == pageSize - 1) {
                writeFlashPage()
                false
            } else {
                true
            }
        }
    }

    private fun startApplicationIfRequested() {
        if (command == CMD_BOOT_APPLICATION) {
            mode = Mode.APPLICATION
        }
    }

    private fun writeFlashPage() {
        val pageStart = address

        if (pageStart == 0) {
            savedResetVector = pageBuffer[0] or (pageBuffer[1] shl 8)
            savedAppVector = pageBuffer[APP_VECTOR_NUM * 2] or (pageBuffer[APP_VECTOR_NUM * 2 + 1] shl 8)

            val resetVector = rjmp(0, bootloaderStart / 2)
            val appVector = 0xc000 or ((savedResetVector - APP_VECTOR_NUM) and 0x0fff)
            pageBuffer[0] = resetVector and 0xff
            pageBuffer[1] = resetVector shr 8
            pageBuffer[APP_VECTOR_NUM * 2] = appVector and 0xff
            pageBuffer[APP_VECTOR_NUM * 2 + 1] = appVector shr 8
        }

        if (pageStart >= bootloaderStart) {
            return
        }

        pageWrites++
        pageBuffer.copyInto(flash, pageStart)

        if (pagesToCorruptOnce.remove(pageStart)) {
            flash[pageStart + pageSize - 1] = flash[pageStart + pageSize - 1] xor 0x55
        }
    }

    private fun bootloaderRead(byteNumber: Int): Int = when (command) {
        CMD_SWITCH_APPLICATION -> "TWIBOOT v3.2".padEnd(16, '\u0000')[byteNumber % 16].code
        CMD_ACCESS_CHIPINFO -> intArrayOf(
            signature shr 16, (signature shr 8) and 0xff, signature and 0xff,
            pageSize,
            bootloaderStart shr 8, bootloaderStart and 0xff,
            0, 0,
        )[byteNumber % 8]

        CMD_ACCESS_FLASH -> {
            val data = when (address) {
                0 -> savedResetVector and 0xff
                1 -> savedResetVector shr 8
                APP_VECTOR_NUM * 2 -> savedAppVector and 0xff
                APP_VECTOR_NUM * 2 + 1 -> savedAppVector shr 8
                else -> flash[address]
            }
            address++
            data
        }

        else -> 0xff
    }
}
