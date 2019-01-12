package eu.slomkowski.octoglow.hardware

import io.dvlopt.linux.i2c.*

abstract class I2CDevice(
        protected val i2c: I2CBus,
        protected val i2cAddress: Int) {

    init {
        require(i2cAddress in 0..127)
        require(i2c.functionalities.can(I2CFunctionality.TRANSACTIONS)) { "I2C bus requires transaction support" }
    }

    protected fun selectSlave() = i2c.selectSlave(i2cAddress)

    fun I2CBuffer.set(index: Int, v: Byte): I2CBuffer = this.set(index, v.toInt())

    suspend fun doTransaction(writeBuffer: I2CBuffer, readBuffer: I2CBuffer) {
        i2c.doTransaction(I2CTransaction(2).apply {
            getMessage(0).apply {
                address = i2cAddress
                buffer = writeBuffer
            }
            getMessage(1).apply {
                address = i2cAddress
                buffer = readBuffer
                flags = I2CFlags().set(I2CFlag.READ)
            }
        })
    }
}