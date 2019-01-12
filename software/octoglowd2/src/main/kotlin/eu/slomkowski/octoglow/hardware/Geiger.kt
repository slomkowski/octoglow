package eu.slomkowski.octoglow.hardware

import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import java.time.Duration

enum class EyeDisplayMode {
    ANIMATION,
    FIXED_VALUE
}

enum class EyeInverterState {
    DISABLED,
    HEATING_LIMITED,
    HEATING_FULL,
    RUNNING
}

data class GeigerCounterState(
        val hasNewCycleStarted: Boolean,
        val numOfCountsInCurrentCycle: Int,
        val numOfCountsInPreviousCycle: Int,
        val cycleLength: Duration)


data class GeigerDeviceState(
        val geigerVoltage: Double,
        val geigerPwmValue: Int,
        val eyeState: EyeInverterState,
        val eyeAnimationState: EyeDisplayMode,
        val eyeVoltage: Double,
        val eyePwmValue: Int)

class Geiger(i2c: I2CBus) : I2CDevice(i2c, 0x12), HasBrightness {

    private val writeBuffer = I2CBuffer(8)
    private val readBuffer = I2CBuffer(8)

    private val cycleMaxDuration = Duration.ofSeconds(0xffff)

    override suspend fun setBrightness(brightness: Int) {
        assert(brightness in 0..5) { "brightness should be in range 0..5" }

        selectSlave()
        i2c.write(writeBuffer.set(0, 7).set(1, brightness), 2)
    }

    suspend fun setEyeEnabled(enabled: Boolean) {
        selectSlave()
        i2c.write(writeBuffer.set(0, 5).set(1, when (enabled) {
            true -> 1
            else -> 0
        }), 2)
    }

    suspend fun getDeviceState(): GeigerDeviceState {
        selectSlave()
        i2c.write(writeBuffer.set(0, 1), 1)
        i2c.read(readBuffer, 8)

        return GeigerDeviceState(
                (0xff * readBuffer.get(1) + readBuffer.get(0)).toDouble(),
                readBuffer.get(2),
                EyeInverterState.values()[readBuffer.get(3)],
                EyeDisplayMode.values()[readBuffer.get(4)],
                (0xff * readBuffer.get(6) + readBuffer.get(5)).toDouble(),
                readBuffer.get(7)
        )
    }

    suspend fun getCounterState(): GeigerCounterState {
        selectSlave()
        i2c.write(writeBuffer.set(0, 2), 1)
        i2c.read(readBuffer, 7)

        return GeigerCounterState(
                readBuffer.get(0) > 0,
                0xff * readBuffer.get(2) + readBuffer.get(1),
                0xff * readBuffer.get(4) + readBuffer.get(3),
                Duration.ofSeconds((0xff * readBuffer.get(6) + readBuffer.get(5)).toLong()))
    }

    suspend fun setCycleLength(duration: Duration) {

        assert(duration > Duration.ZERO) { "duration has to be non-zero" }
        assert(duration < cycleMaxDuration) { "duration can be max $cycleMaxDuration" }
        val seconds = duration.seconds.toInt()

        selectSlave()
        i2c.write(writeBuffer
                .set(0, 3)
                .set(1, seconds)
                .set(2, seconds shl 8), 3)
    }


}