package eu.slomkowski.octoglow

import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import io.dvlopt.linux.i2c.I2CFunctionality

abstract class I2CDevice(
        protected val i2c: I2CBus,
        protected val i2cAddress: Int) {

    init {
        require(i2cAddress in 0..127)
        require(i2c.functionalities.can(I2CFunctionality.TRANSACTIONS)) { "I2C bus requires transaction support" }
    }

    protected fun selectSlave() = i2c.selectSlave(i2cAddress)

    fun I2CBuffer.set(index: Int, v: Byte): I2CBuffer = this.set(index, v.toInt())
}