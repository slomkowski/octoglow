package eu.slomkowski.octoglow.octoglowd.hardware

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.ExperimentalTime

enum class DacChannel(val number: Int) {
    C1(0b00),
    C2(0b01)
}

class Dac(hardware: Hardware) : I2CDevice(hardware, 0x4f, logger) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun initDevice() {
        setToMiddle()
    }

    override suspend fun closeDevice() {
        setToMiddle()
    }

    // we set to middle to avoid abrupt bumping of the needle
    private suspend fun setToMiddle() {
        setValue(DacChannel.C1, 127)
        setValue(DacChannel.C2, 127)
    }

    suspend fun setValue(channel: DacChannel, value: Int) {
        require(value in (0..255))
        doWrite(0b00_01_0_00_0 or (channel.number shl 1), value, 0)
    }
}