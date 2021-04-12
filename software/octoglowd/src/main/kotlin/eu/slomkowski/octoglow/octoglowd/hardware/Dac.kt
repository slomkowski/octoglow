package eu.slomkowski.octoglow.octoglowd.hardware

import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime

enum class DacChannel(val number: Int) {
    C1(0b00),
    C2(0b01)
}

@ExperimentalTime
class Dac(hardware: Hardware) : I2CDevice(hardware, 0x4f) {

    init {
        setToZero()
    }

    override suspend fun closeDevice() {
        setToZero()
    }

    private fun setToZero() {
        runBlocking {
            setValue(DacChannel.C1, 0)
            setValue(DacChannel.C2, 0)
        }
    }

    suspend fun setValue(channel: DacChannel, value: Int) {
        require(value in (0..255))
        doWrite(0b00_01_0_00_0 or (channel.number shl 1), value, 0)
    }
}