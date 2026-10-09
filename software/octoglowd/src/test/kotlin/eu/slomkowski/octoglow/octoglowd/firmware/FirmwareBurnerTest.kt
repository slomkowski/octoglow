package eu.slomkowski.octoglow.octoglowd.firmware

import eu.slomkowski.octoglow.octoglowd.firmware.TwibootDeviceEmulator.Companion.APP_VECTOR_NUM
import eu.slomkowski.octoglow.octoglowd.firmware.TwibootDeviceEmulator.Companion.rjmp
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class FirmwareBurnerTest {

    /**
     * Application-like image: vector table of RJMPs (reset jumps to word 0x31), then random code.
     */
    private fun createImage(size: Int): Map<Int, Int> {
        val random = Random(size)
        val data = (0 until size).associateWith { random.nextInt(256) }.toMutableMap()
        for (vector in 0 until 19) {
            val opcode = rjmp(vector, 0x31)
            data[vector * 2] = opcode and 0xff
            data[vector * 2 + 1] = opcode shr 8
        }
        return data
    }

    @Test
    fun `burns, verifies and starts the application`() = runTest {
        val device = TwibootDeviceEmulator()
        val data = createImage(2890)
        val progress = mutableListOf<Pair<Int, Int>>()

        FirmwareBurner(device, FirmwareTarget.CLOCK_DISPLAY, onProgress = { w, t -> progress += w to t }).burn(FirmwareImage(data))

        assertThat(device.mode).isEqualTo(TwibootDeviceEmulator.Mode.APPLICATION)

        // the vector table is patched by the bootloader
        assertThat(device.flashWord(0)).isEqualTo(rjmp(0, 0x0c40 / 2))
        assertThat(device.flashWord(APP_VECTOR_NUM * 2)).isEqualTo(rjmp(APP_VECTOR_NUM, 0x31))

        val vectorBytes = setOf(0, 1, APP_VECTOR_NUM * 2, APP_VECTOR_NUM * 2 + 1)
        data.filterKeys { it !in vectorBytes }.forEach { (address, value) ->
            assertThat(device.flash[address]).describedAs("byte at 0x%x", address).isEqualTo(value)
        }

        // the rest of the last page is padded with 0xff
        assertThat(device.flash.sliceArray(2890 until 2944).all { it == 0xff }).isTrue()

        assertThat(progress).hasSize(46)
        assertThat(progress.last()).isEqualTo(46 to 46)
        assertThat(device.pageWrites).isEqualTo(46)
    }

    @Test
    fun `rewrites a page which failed verification`() = runTest {
        val device = TwibootDeviceEmulator().apply { pagesToCorruptOnce += 0x0140 }
        val data = createImage(1000)

        FirmwareBurner(device, FirmwareTarget.CLOCK_DISPLAY).burn(FirmwareImage(data))

        assertThat(device.pageWrites).isEqualTo(17)
        assertThat(device.flash[0x0140 + 63]).isEqualTo(data[0x0140 + 63])
    }

    @Test
    fun `connects to the bootloader which is already running`() = runTest {
        val device = TwibootDeviceEmulator().apply { mode = TwibootDeviceEmulator.Mode.BOOTLOADER }

        FirmwareBurner(device, FirmwareTarget.CLOCK_DISPLAY).burn(FirmwareImage(createImage(100)))

        assertThat(device.mode).isEqualTo(TwibootDeviceEmulator.Mode.APPLICATION)
    }

    @Test
    fun `refuses an image overlapping the bootloader`() = runTest {
        val device = TwibootDeviceEmulator()

        val e = assertThrows<FirmwareBurnException> {
            FirmwareBurner(device, FirmwareTarget.CLOCK_DISPLAY).burn(FirmwareImage(createImage(0x0c41)))
        }

        assertThat(e.message).contains("application flash is only 3136 bytes")
        assertThat(device.pageWrites).isZero()
    }

    @Test
    fun `refuses a device with a different signature`() = runTest {
        val device = TwibootDeviceEmulator(signature = 0x1e930a)

        assertThrows<FirmwareBurnException> {
            FirmwareBurner(device, FirmwareTarget.CLOCK_DISPLAY).burn(FirmwareImage(createImage(100)))
        }
        assertThat(device.pageWrites).isZero()
    }

    @Test
    fun `fails when the bootloader doesn't respond`() = runTest {
        val device = TwibootDeviceEmulator().apply { mode = TwibootDeviceEmulator.Mode.DEAD }

        val e = assertThrows<FirmwareBurnException> {
            FirmwareBurner(device, FirmwareTarget.CLOCK_DISPLAY).burn(FirmwareImage(createImage(100)))
        }

        assertThat(e.message).contains("bootloader didn't respond")
    }

    @Test
    fun `burns the front display without patching the vector table`() = runTest {
        val device = TwibootDeviceEmulator(FirmwareTarget.FRONT_DISPLAY)
        val data = createImage(4836)
        val progress = mutableListOf<Pair<Int, Int>>()

        FirmwareBurner(device, FirmwareTarget.FRONT_DISPLAY, onProgress = { w, t -> progress += w to t }).burn(FirmwareImage(data))

        assertThat(device.mode).isEqualTo(TwibootDeviceEmulator.Mode.APPLICATION)

        data.forEach { (address, value) ->
            assertThat(device.flash[address]).describedAs("byte at 0x%x", address).isEqualTo(value)
        }
        assertThat(device.flash.sliceArray(4836 until 4864).all { it == 0xff }).isTrue()

        assertThat(progress.last()).isEqualTo(76 to 76)
        assertThat(device.pageWrites).isEqualTo(76)

        // TWI acknowledges the byte which completes a page or starts the application
        assertThat(device.naks).isZero()
    }

    @Test
    fun `burns the front display application of maximum size`() = runTest {
        val device = TwibootDeviceEmulator(FirmwareTarget.FRONT_DISPLAY)

        FirmwareBurner(device, FirmwareTarget.FRONT_DISPLAY).burn(FirmwareImage(createImage(0x1800)))

        assertThat(device.pageWrites).isEqualTo(96)
    }

    @Test
    fun `refuses an image overlapping the front display bootloader`() = runTest {
        val device = TwibootDeviceEmulator(FirmwareTarget.FRONT_DISPLAY)

        val e = assertThrows<FirmwareBurnException> {
            FirmwareBurner(device, FirmwareTarget.FRONT_DISPLAY).burn(FirmwareImage(createImage(0x1801)))
        }

        assertThat(e.message).contains("application flash is only 6144 bytes")
        assertThat(device.pageWrites).isZero()
    }

    /**
     * MSP430 image as in the hex file: code from the start of the flash, the vector table just below the bootloader.
     */
    private fun createGeigerImage(codeSize: Int): Map<Int, Int> {
        val random = Random(codeSize)
        val data = (0xc000 until 0xc000 + codeSize).associateWith { random.nextInt(256) }.toMutableMap()
        for (address in 0xfbe0 until 0xfc00 step 2) {
            data[address] = 0x0c
            data[address + 1] = 0xc0
        }
        return data
    }

    @Test
    fun `burns the geiger with the addresses relative to the flash start`() = runTest {
        val device = TwibootDeviceEmulator(FirmwareTarget.GEIGER)
        val data = createGeigerImage(9632)
        val progress = mutableListOf<Pair<Int, Int>>()

        FirmwareBurner(device, FirmwareTarget.GEIGER, onProgress = { w, t -> progress += w to t }).burn(FirmwareImage(data))

        assertThat(device.mode).isEqualTo(TwibootDeviceEmulator.Mode.APPLICATION)

        data.forEach { (address, value) ->
            assertThat(device.flash[address - 0xc000]).describedAs("byte at 0x%x", address).isEqualTo(value)
        }
        assertThat(device.flash.sliceArray(9632 until 0x3be0).all { it == 0xff }).isTrue()

        // the vector table is at the end, so the whole application flash is written
        assertThat(progress.last()).isEqualTo(240 to 240)
        assertThat(device.pageWrites).isEqualTo(240)
        assertThat(device.naks).isZero()
    }

    @Test
    fun `refuses a geiger image below the flash`() = runTest {
        val device = TwibootDeviceEmulator(FirmwareTarget.GEIGER)
        val data = createGeigerImage(100) + (0xbfff to 0)

        val e = assertThrows<FirmwareBurnException> {
            FirmwareBurner(device, FirmwareTarget.GEIGER).burn(FirmwareImage(data))
        }

        assertThat(e.message).contains("below the flash start 0xc000")
        assertThat(device.mode).isEqualTo(TwibootDeviceEmulator.Mode.APPLICATION)
    }

    @Test
    fun `refuses a geiger image overlapping the bootloader`() = runTest {
        val device = TwibootDeviceEmulator(FirmwareTarget.GEIGER)
        val data = createGeigerImage(100) + (0xfc00 to 0)

        val e = assertThrows<FirmwareBurnException> {
            FirmwareBurner(device, FirmwareTarget.GEIGER).burn(FirmwareImage(data))
        }

        assertThat(e.message).contains("application flash is only 15360 bytes")
        assertThat(device.pageWrites).isZero()
    }
}
