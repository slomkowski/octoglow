package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToString
import eu.slomkowski.octoglow.octoglowd.toList
import eu.slomkowski.octoglow.octoglowd.trySeveralTimes
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import mu.KLogging
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
        val currentCycleProgress: Duration,
        val cycleLength: Duration) {
    companion object {
        private val invalidBufferContent = listOf(0, 255, 255, 255, 255, 255, 255, 255, 255)

        const val SIZE_IN_BYTES = 9

        fun parse(readBuffer: I2CBuffer): GeigerCounterState {
            require(readBuffer.length == SIZE_IN_BYTES)
            val buff = readBuffer.toList()

            check(buff != invalidBufferContent) { "read buffer has characteristic invalid pattern" }

            return GeigerCounterState(
                    when (buff[0]) {
                        0 -> false
                        1 -> true
                        else -> throw IllegalStateException("invalid value for boolean flag: ${buff.get(0)}")
                    },
                    (buff[2] shl 8) + buff[1],
                    (buff[4] shl 8) + buff[3],
                    Duration.ofSeconds(((buff[6] shl 8) + buff[5]).toLong()),
                    Duration.ofSeconds(((buff[8] shl 8) + buff[7]).toLong()))
        }
    }
}

data class GeigerDeviceState(
        val geigerVoltage: Double, //todo
        val geigerPwmValue: Int,
        val eyeState: EyeInverterState,
        val eyeAnimationState: EyeDisplayMode,
        val eyeVoltage: Double,
        val eyePwmValue: Int) {
    companion object {
        private val invalidBufferContent = listOf(255, 255, 255, 255, 255, 255, 255)

        const val SIZE_IN_BYTES = 8

        private const val GEIGER_ADC_SCALING_FACTOR: Double = 2.5 / 1024 / (4.7 / (4.7 + 4 * 470))

        private const val EYE_ADC_SCALING_FACTOR: Double = 2.5 / 1024 / (4.7 / (4.7 + 3 * 180))

        fun parse(readBuffer: I2CBuffer): GeigerDeviceState {
            require(readBuffer.length == SIZE_IN_BYTES)
            val buff = readBuffer.toList()

            check(buff.subList(1, buff.size) != invalidBufferContent) { "read buffer has characteristic invalid pattern" }

            return GeigerDeviceState(
                    GEIGER_ADC_SCALING_FACTOR * ((buff[1] shl 8) + buff[0]).toDouble(),
                    buff[2],
                    EyeInverterState.values()[buff[3]],
                    EyeDisplayMode.values()[buff[4]],
                    EYE_ADC_SCALING_FACTOR * ((buff[6] shl 8) + buff[5]).toDouble(),
                    buff[7])
        }
    }
}

class Geiger(ctx: CoroutineContext, i2c: I2CBus) : I2CDevice(ctx, i2c, 0x12), HasBrightness {

    companion object : KLogging() {
        private val cycleMaxDuration = Duration.ofSeconds(0xffff)

        private const val I2C_READ_TRIES = 4
    }

    override fun close() {}

    override suspend fun setBrightness(brightness: Int) {
        assert(brightness in 0..5) { "brightness should be in range 0..5" }
        doWrite(7, brightness)
    }

    suspend fun getDeviceState(): GeigerDeviceState = trySeveralTimes(I2C_READ_TRIES, logger) {
        val readBuffer = doTransaction(listOf(1), GeigerDeviceState.SIZE_IN_BYTES)
        logger.debug { "Device state buffer: ${readBuffer.contentToString()}." }
        GeigerDeviceState.parse(readBuffer)
    }

    suspend fun getCounterState(): GeigerCounterState = trySeveralTimes(I2C_READ_TRIES, logger) {
        val readBuffer = doTransaction(listOf(2), GeigerCounterState.SIZE_IN_BYTES)
        logger.debug { "Counter state buffer: ${readBuffer.contentToString()}." }
        GeigerCounterState.parse(readBuffer)
    }

    suspend fun setCycleLength(duration: Duration) {

        assert(duration > Duration.ZERO) { "duration has to be non-zero" }
        assert(duration < cycleMaxDuration) { "duration can be max $cycleMaxDuration" }
        val seconds = duration.seconds.toInt()

        doWrite(3, 0xff and seconds, 0xff and (seconds shr 8))
    }

    suspend fun reset() {
        doWrite(4)
    }

    suspend fun setEyeConfiguration(enabled: Boolean, mode: EyeDisplayMode = EyeDisplayMode.ANIMATION) {
        doWrite(5, when (enabled) {
            true -> 1
            else -> 0
        }, mode.ordinal)
    }

    suspend fun setEyeValue(value: Int) {
        require(value in 0..255)
        doWrite(6, value)
    }
}