package eu.slomkowski.octoglow.octoglowd.hardware

import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

enum class DacChannel(val number: Int) {
    C1(0b00),
    C2(0b01)
}

class Dac(ctx: CoroutineContext, i2cBus: I2CBus) : I2CDevice(ctx, i2cBus, 0x4f) {

    init {
        setToZero()
    }

    override fun close() {
        setToZero()
    }

    private fun setToZero() {
        runBlocking(threadContext) {
            setValue(DacChannel.C1, 0)
            setValue(DacChannel.C2, 0)
        }
    }

    suspend fun setValue(channel: DacChannel, value: Int) {
        require(value in (0..255))
        doWrite(0b00_01_0_00_0 or (channel.number shl 1), value, 0)
    }
}