package eu.slomkowski.octoglow.octoglowd.firmware

import eu.slomkowski.octoglow.octoglowd.hardware.*
import eu.slomkowski.octoglow.octoglowd.hardware.CustomI2cDevice.Companion.calculateCcittCrc8
import io.mockk.mockk
import java.io.IOException
import kotlin.time.Duration

/**
 * A board with the twiboot bootloader, on the I2C bus level: the application handles ENTER_BOOTLOADER
 * and the command used to check it after the update, the bootloader follows the firmware:
 *
 * - [FirmwareTarget.CLOCK_DISPLAY]: firmware/clock-display/bootloader/protocol.cpp, USI: the vector table
 *   is patched and the last byte of a page is NAKed.
 * - [FirmwareTarget.FRONT_DISPLAY]: firmware/front-display/bootloader/twiboot.cpp, TWI: boot section,
 *   no patching, the byte which ends the command is ACKed, only the following bytes are NAKed.
 */
class TwibootDeviceEmulator(
    private val target: FirmwareTarget = FirmwareTarget.CLOCK_DISPLAY,
    private val signature: Int = target.signature,
) : Hardware {

    private class Board(
        val appAddress: Int,
        val bootloaderStart: Int,
        val flashSize: Int,
        val usi: Boolean,
        val enterBootloaderCommand: Int,
        val checkCommand: Int,
        val checkReply: IntArray,
    )

    companion object {
        const val APP_VECTOR_NUM = 9
        const val PAGE_SIZE = 64

        private const val CMD_WAIT = 0x00
        private const val CMD_SWITCH_APPLICATION = 0x01
        private const val CMD_ACCESS_MEMORY = 0x02
        private const val CMD_ACCESS_CHIPINFO = 0x12
        private const val CMD_ACCESS_FLASH = 0x22
        private const val CMD_BOOT_APPLICATION = 0x21

        private val boards = mapOf(
            // GET_RELAY_STATE
            FirmwareTarget.CLOCK_DISPLAY to Board(0x10, 0x0c40, 4096, usi = true, 7, 6, intArrayOf(0, 6, 0)),
            // READ_END_YEAR_OF_CONSTRUCTION
            FirmwareTarget.FRONT_DISPLAY to Board(0x14, 0x1800, 8192, usi = false, 10, 8, intArrayOf(0, 8, 26)),
        )

        fun rjmp(fromWord: Int, toWord: Int) = 0xc000 or ((toWord - fromWord - 1) and 0x0fff)

        private fun nak(): Nothing = throw IOException("native I2C transaction error: errno 121")
        private fun noDevice(): Nothing = throw IOException("native I2C transaction error: errno 6")
    }

    enum class Mode { APPLICATION, BOOTLOADER, DEAD }

    private val board = boards.getValue(target)

    var mode = Mode.APPLICATION

    val flash = IntArray(board.flashSize) { 0xff }

    /**
     * Page addresses which are written incorrectly once, to test the verification.
     */
    val pagesToCorruptOnce = mutableSetOf<Int>()

    var pageWrites = 0
        private set

    /**
     * Number of bootloader transfers which ended with NAK, reported by Linux as an I/O error.
     */
    var naks = 0
        private set

    // bootloader state, like in the firmware
    private var command = CMD_WAIT
    private var address = 0
    private val pageBuffer = IntArray(PAGE_SIZE)
    private var savedResetVector = 0
    private var savedAppVector = 0

    override val clockDisplay = ClockDisplay(this)
    override val frontDisplay: FrontDisplay = when (target) {
        FirmwareTarget.FRONT_DISPLAY -> FrontDisplayReal(this)
        else -> mockk<FrontDisplay>(relaxed = true)
    }
    override val geiger = mockk<Geiger>(relaxed = true)
    override val dac = mockk<Dac>(relaxed = true)
    override val scd40 = mockk<Scd40>(relaxed = true)
    override val bme280 = mockk<Bme280>(relaxed = true)

    fun flashWord(byteAddress: Int) = flash[byteAddress] or (flash[byteAddress + 1] shl 8)

    /**
     * Simulates a reset: the patched reset vector or BOOTRST fuse always starts the bootloader.
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
            i2cAddress == target.bootloaderI2cAddress && mode == Mode.BOOTLOADER -> bootloaderWrite(writeData)
            else -> noDevice()
        }
    }

    override suspend fun doTransaction(i2cAddress: Int, writeData: IntArray, bytesToRead: Int, delayBetweenWriteAndRead: Duration): IntArray {
        return when {
            i2cAddress == board.appAddress && mode == Mode.APPLICATION -> applicationTransaction(writeData, bytesToRead)
            i2cAddress == target.bootloaderI2cAddress && mode == Mode.BOOTLOADER -> {
                bootloaderWrite(writeData)
                IntArray(bytesToRead) { bootloaderRead(it) }
            }

            else -> noDevice()
        }
    }

    private fun applicationTransaction(writeData: IntArray, bytesToRead: Int): IntArray {
        check(calculateCcittCrc8(writeData, 1..<writeData.size) == writeData[0]) { "invalid CRC" }

        val reply = when (writeData[1]) {
            board.enterBootloaderCommand -> {
                check(writeData.sliceArray(2..5).contentEquals("BOOT".map { it.code }.toIntArray()))
                resetIntoBootloader()
                intArrayOf(0, board.enterBootloaderCommand)
            }

            board.checkCommand -> board.checkReply.copyOf()
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
                // USI NAKs this byte, TWI only the following ones; Linux reports NAK as an error
                if (board.usi || byteNumber != data.lastIndex) {
                    naks++
                    nak()
                }
                return
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
            if (position == PAGE_SIZE - 1) {
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

        if (board.usi && pageStart == 0) {
            savedResetVector = pageBuffer[0] or (pageBuffer[1] shl 8)
            savedAppVector = pageBuffer[APP_VECTOR_NUM * 2] or (pageBuffer[APP_VECTOR_NUM * 2 + 1] shl 8)

            val resetVector = rjmp(0, board.bootloaderStart / 2)
            val appVector = 0xc000 or ((savedResetVector - APP_VECTOR_NUM) and 0x0fff)
            pageBuffer[0] = resetVector and 0xff
            pageBuffer[1] = resetVector shr 8
            pageBuffer[APP_VECTOR_NUM * 2] = appVector and 0xff
            pageBuffer[APP_VECTOR_NUM * 2 + 1] = appVector shr 8
        }

        // the TWI bootloader also ignores unaligned pages
        if (pageStart >= board.bootloaderStart || (!board.usi && pageStart % PAGE_SIZE != 0)) {
            return
        }

        pageWrites++
        pageBuffer.copyInto(flash, pageStart)

        if (pagesToCorruptOnce.remove(pageStart)) {
            flash[pageStart + PAGE_SIZE - 1] = flash[pageStart + PAGE_SIZE - 1] xor 0x55
        }
    }

    private fun bootloaderRead(byteNumber: Int): Int = when (command) {
        CMD_SWITCH_APPLICATION -> "TWIBOOT v3.2".padEnd(16, '\u0000')[byteNumber % 16].code
        CMD_ACCESS_CHIPINFO -> intArrayOf(
            signature shr 16, (signature shr 8) and 0xff, signature and 0xff,
            PAGE_SIZE,
            board.bootloaderStart shr 8, board.bootloaderStart and 0xff,
            0, 0,
        )[byteNumber % 8]

        CMD_ACCESS_FLASH -> {
            val data = when {
                !board.usi -> flash[address]
                address == 0 -> savedResetVector and 0xff
                address == 1 -> savedResetVector shr 8
                address == APP_VECTOR_NUM * 2 -> savedAppVector and 0xff
                address == APP_VECTOR_NUM * 2 + 1 -> savedAppVector shr 8
                else -> flash[address]
            }
            address++
            data
        }

        else -> 0xff
    }
}
