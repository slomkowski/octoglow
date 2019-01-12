package eu.slomkowski.octoglow.hardware

import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus

data class OutdoorWeatherReport(
        val temperature: Double,
        val humidity: Double,
        val batteryIsWeak: Boolean,
        val alreadyReadFlag: Boolean) {
    init {
        require(temperature in -40.0..60.0)
        require(humidity in 0.0..100.0)
    }
}

class ClockDisplay(i2c: I2CBus) : I2CDevice(i2c, 0x10), HasBrightness {

    private val UPPER_DOT: Int = 1 shl (14 % 8)
    private val LOWER_DOT: Int = 1 shl (13 % 8)

    private val writeBuffer = I2CBuffer(8)

    override suspend fun setBrightness(brightness: Int) {
        selectSlave()
        i2c.smbus.writeWord(3, brightness)
    }

    suspend fun getOutdoorWeatherReport(): OutdoorWeatherReport {
        val readBuffer = I2CBuffer(6)
        doTransaction(I2CBuffer(1).set(0, 4), readBuffer)

        return OutdoorWeatherReport(
                (256.0 * readBuffer[2].toDouble() + readBuffer[1].toDouble()) / 10.0,
                readBuffer[3].toDouble(),
                readBuffer[4] == 1,
                readBuffer[5] == 1)
    }

    suspend fun setDisplay(hours: Int, minutes: Int, upperDot: Boolean, lowerDot: Boolean) {
        require(hours in 0..24)
        require(minutes in 0..60)

        val dots = if (upperDot) {
            UPPER_DOT
        } else {
            0
        } or if (lowerDot) {
            LOWER_DOT
        } else {
            0
        }

        selectSlave()
        i2c.write(writeBuffer
                .set(0, 1)
                .set(1, 0x30 + hours / 10)
                .set(2, 0x30 + hours % 10)
                .set(3, 0x30 + minutes / 10)
                .set(4, 0x30 + minutes % 10)
                .set(5, dots), 6)
    }
}
