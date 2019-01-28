package eu.slomkowski.octoglow.octoglowd.hardware

import io.dvlopt.linux.i2c.I2CBus
import java.time.Duration
import kotlin.coroutines.CoroutineContext

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

class Geiger(ctx: CoroutineContext, i2c: I2CBus) : I2CDevice(ctx, i2c, 0x12), HasBrightness {

    companion object {
        private val cycleMaxDuration = Duration.ofSeconds(0xffff)
    }

    override fun close() {
        // todo put closing code later
    }

    override suspend fun setBrightness(brightness: Int) {
        assert(brightness in 0..5) { "brightness should be in range 0..5" }
        doWrite(7, brightness)
    }

    suspend fun setEyeEnabled(enabled: Boolean) {
        doWrite(5, when (enabled) {
            true -> 1
            else -> 0
        })
    }

    suspend fun getDeviceState(): GeigerDeviceState {
        val readBuffer = doTransaction(listOf(1), 8)

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
        val readBuffer = doTransaction(listOf(2), 7)

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

        doWrite(3, 0xff and seconds, seconds shl 8)
    }
}