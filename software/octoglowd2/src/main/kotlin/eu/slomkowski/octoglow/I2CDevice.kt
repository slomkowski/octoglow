package eu.slomkowski.octoglow

import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus

abstract class I2CDevice(
        protected val i2c: I2CBus,
        protected val i2cAddress: Int) {

    init {
        assert(i2cAddress in 0..127)
    }

    protected fun selectSlave() = i2c.selectSlave(i2cAddress)

    fun I2CBuffer.set(index: Int, v: Byte): I2CBuffer = this.set(index, v.toInt())
}