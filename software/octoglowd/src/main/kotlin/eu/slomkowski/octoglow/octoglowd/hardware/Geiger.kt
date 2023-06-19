package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.contentToString
import eu.slomkowski.octoglow.octoglowd.toList
import eu.slomkowski.octoglow.octoglowd.trySeveralTimes
import io.dvlopt.linux.i2c.I2CBuffer
import mu.KLogging
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Duration.Companion.seconds

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

@ExperimentalTime
data class GeigerCounterState(
    val hasNewCycleStarted: Boolean,
    val hasCycleEverCompleted: Boolean,
    val numOfCountsInCurrentCycle: Int,
    val numOfCountsInPreviousCycle: Int,
    val currentCycleProgress: Duration,
    val cycleLength: Duration
) {
    companion object {
        private val invalidBufferContent = listOf(255, 255, 255, 255, 255, 255, 255, 255)

        const val SIZE_IN_BYTES = 9

        fun parse(readBuffer: I2CBuffer): GeigerCounterState {
            require(readBuffer.length == SIZE_IN_BYTES)
            val buff = readBuffer.toList()

            check(
                buff.subList(
                    1,
                    buff.size
                ) != invalidBufferContent
            ) { "read buffer has characteristic invalid pattern" }

            return GeigerCounterState(
                when (buff[0] and 0b1) {
                    1 -> true
                    else -> false
                },
                when (buff[0] and 0b10) {
                    0b10 -> true
                    else -> false
                },
                (buff[2] shl 8) + buff[1],
                (buff[4] shl 8) + buff[3],
                ((buff[6] shl 8) + buff[5]).seconds,
                ((buff[8] shl 8) + buff[7]).seconds
            )
        }
    }
}

data class GeigerDeviceState(
    val geigerVoltage: Double,
    val geigerPwmValue: Int,
    val eyeState: EyeInverterState,
    val eyeAnimationState: EyeDisplayMode,
    val eyeVoltage: Double,
    val eyePwmValue: Int
) {
    companion object {
        private val invalidBufferContent = listOf(255, 255, 255, 255, 255, 255, 255)

        const val SIZE_IN_BYTES = 8

        private const val GEIGER_ADC_SCALING_FACTOR: Double = 2.5 / 1024 / (4.7 / (4.7 + 4 * 470))

        private const val EYE_ADC_SCALING_FACTOR: Double = 2.5 / 1024 / (4.7 / (4.7 + 3 * 180))

        fun parse(readBuffer: I2CBuffer): GeigerDeviceState {
            require(readBuffer.length == SIZE_IN_BYTES)
            val buff = readBuffer.toList()

            check(
                buff.subList(
                    1,
                    buff.size
                ) != invalidBufferContent
            ) { "read buffer has characteristic invalid pattern" }

            return GeigerDeviceState(
                GEIGER_ADC_SCALING_FACTOR * ((buff[1] shl 8) + buff[0]).toDouble(),
                buff[2],
                EyeInverterState.values()[buff[3]],
                EyeDisplayMode.values()[buff[4]],
                EYE_ADC_SCALING_FACTOR * ((buff[6] shl 8) + buff[5]).toDouble(),
                buff[7]
            )
        }
    }

    init {
        require(geigerVoltage in 0.0..500.0)
        require(eyeVoltage in 0.0..300.0)
    }
}

@ExperimentalTime
class Geiger(hardware: Hardware) : I2CDevice(hardware, 0x12), HasBrightness {

    companion object : KLogging() {
        private val CYCLE_MAX_DURATION: Duration = 0xffff.seconds

        private const val I2C_READ_TRIES = 5
    }

    override suspend fun closeDevice() {
        setBrightness(3)
    }

    override suspend fun setBrightness(brightness: Int) {
        assert(brightness in 0..MAX_BRIGHTNESS) { "brightness should be in range 0..5" }
        doWrite(7, brightness)
    }

    suspend fun getDeviceState(): GeigerDeviceState = trySeveralTimes(I2C_READ_TRIES, logger) {
        val readBuffer = doTransaction(listOf(1), GeigerDeviceState.SIZE_IN_BYTES)
        logger.trace { "Device state buffer: ${readBuffer.contentToString()}." }
        GeigerDeviceState.parse(readBuffer)
    }

    suspend fun getCounterState(): GeigerCounterState = trySeveralTimes(I2C_READ_TRIES, logger) {
        val readBuffer = doTransaction(listOf(2), GeigerCounterState.SIZE_IN_BYTES)
        logger.trace { "Counter state buffer: ${readBuffer.contentToString()}." }
        GeigerCounterState.parse(readBuffer)
    }

    suspend fun setCycleLength(duration: Duration) {

        assert(duration > Duration.ZERO) { "duration has to be non-zero" }
        assert(duration < CYCLE_MAX_DURATION) { "duration can be max $CYCLE_MAX_DURATION" }
        val seconds = duration.inWholeSeconds.toInt()

        doWrite(3, 0xff and seconds, 0xff and (seconds shr 8))
    }

    suspend fun reset() {
        doWrite(4)
    }

    suspend fun setEyeConfiguration(enabled: Boolean, mode: EyeDisplayMode = EyeDisplayMode.ANIMATION) {
        doWrite(
            5, when (enabled) {
                true -> 1
                else -> 0
            }, mode.ordinal
        )
    }

    suspend fun setEyeValue(value: Int) {
        require(value in 0..255)
        doWrite(6, value)
    }
}