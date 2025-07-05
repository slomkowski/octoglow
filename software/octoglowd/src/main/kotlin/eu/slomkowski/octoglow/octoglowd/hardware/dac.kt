package eu.slomkowski.octoglow.octoglowd.hardware

enum class DacChannel(val number: Int) {
    C1(0b00),
    C2(0b01),
}

class Dac(hardware: Hardware) : FactoryMadeI2cDevice(hardware, 0x4f) {

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