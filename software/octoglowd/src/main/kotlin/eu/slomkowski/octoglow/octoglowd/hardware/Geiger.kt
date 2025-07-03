package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.trySeveralTimes
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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

data class GeigerCounterState(
    val hasNewCycleStarted: Boolean,
    val hasCycleEverCompleted: Boolean,
    val numOfCountsInCurrentCycle: Int,
    val numOfCountsInPreviousCycle: Int,
    val currentCycleProgress: Duration,
    val cycleLength: Duration,
) {
    companion object {
        private val invalidBufferContent = intArrayOf(255, 255, 255, 255, 255, 255, 255, 255, 255, 255)

        const val SIZE_IN_BYTES = 11

        fun parse(buff: IntArray): GeigerCounterState {
            require(buff.size == SIZE_IN_BYTES)

            check(!buff.sliceArray(1 until buff.size).contentEquals(invalidBufferContent)) { "read buffer has characteristic invalid pattern" }
            check(buff[1] == 2) { "invalid command identifier - expected 2 but got ${buff[1]}" }

            return GeigerCounterState(
                when (buff[2] and 0b1) {
                    1 -> true
                    else -> false
                },
                when (buff[2] and 0b10) {
                    0b10 -> true
                    else -> false
                },
                (buff[4] shl 8) + buff[3],
                (buff[6] shl 8) + buff[5],
                ((buff[8] shl 8) + buff[7]).seconds,
                ((buff[10] shl 8) + buff[9]).seconds
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
    val eyePwmValue: Int,
) {
    companion object {
        private val invalidBufferContent = intArrayOf(255, 255, 255, 255, 255, 255, 255, 255, 255)

        const val SIZE_IN_BYTES = 10

        private const val GEIGER_ADC_SCALING_FACTOR: Double = 2.5 / 1024 / (4.7 / (4.7 + 4 * 470))

        private const val EYE_ADC_SCALING_FACTOR: Double = 2.5 / 1024 / (4.7 / (4.7 + 3 * 180))

        fun parse(buff: IntArray): GeigerDeviceState {
            require(buff.size == SIZE_IN_BYTES)

            check(buff[1] == 1) { "invalid command identifier - expected 1 but got ${buff[1]}" }
            check(!buff.sliceArray(1 until buff.size).contentEquals(invalidBufferContent)) { "read buffer has characteristic invalid pattern" }

            val geigerAdcReadout = (buff[3] shl 8) + buff[2]
            val eyeAdcReadout = (buff[8] shl 8) + buff[7]

            return GeigerDeviceState(
                GEIGER_ADC_SCALING_FACTOR * geigerAdcReadout.toDouble(),
                buff[4],
                EyeInverterState.entries[buff[5]],
                EyeDisplayMode.entries[buff[6]],
                EYE_ADC_SCALING_FACTOR * eyeAdcReadout.toDouble(),
                buff[8],
            )
        }
    }

    init {
        require(geigerVoltage in 0.0..500.0)
        require(eyeVoltage in 0.0..300.0)
    }
}

class Geiger(hardware: Hardware) : I2CDevice(hardware, 0x12, logger), HasBrightness {

    companion object {
        private val logger = KotlinLogging.logger {}

        private val CYCLE_MAX_DURATION: Duration = 0xffff.seconds

        private const val I2C_READ_TRIES = 6

        private val getDeviceStateCmd = createCommandWithCrc(1)
        private val getGeigerStateCmd = createCommandWithCrc(2)
    }

    override suspend fun initDevice() {
        // nothing to do
    }

    override suspend fun closeDevice() {
        setBrightness(3)
    }

    override suspend fun setBrightness(brightness: Int) {
        assert(brightness in 0..MAX_BRIGHTNESS) { "brightness should be in range 0..5" }
        sendCommand(7, brightness)
    }

    suspend fun getDeviceState(): GeigerDeviceState = trySeveralTimes(I2C_READ_TRIES, logger, "getDeviceState()") {
        val readBuffer = doTransaction(getDeviceStateCmd, GeigerDeviceState.SIZE_IN_BYTES, delayBetweenWriteAndRead = 1.milliseconds) //todo poprawić delay
        logger.trace { "Device state buffer: ${readBuffer.contentToString()}." }
        verifyResponse(getDeviceStateCmd, readBuffer)
        GeigerDeviceState.parse(readBuffer)
    }

    suspend fun getCounterState(): GeigerCounterState = trySeveralTimes(I2C_READ_TRIES, logger, "getCounterState()") {
        val readBuffer = doTransaction(getGeigerStateCmd, GeigerCounterState.SIZE_IN_BYTES, delayBetweenWriteAndRead = 1.milliseconds) //todo poprawić delay
        logger.trace { "Counter state buffer: ${readBuffer.contentToString()}." }
        verifyResponse(getGeigerStateCmd, readBuffer)
        GeigerCounterState.parse(readBuffer)
    }

    suspend fun setCycleLength(duration: Duration) {
        assert(duration > Duration.ZERO) { "duration has to be non-zero" }
        assert(duration < CYCLE_MAX_DURATION) { "duration can be max $CYCLE_MAX_DURATION" }
        val seconds = duration.inWholeSeconds.toInt()

        sendCommand(3, 0xff and seconds, 0xff and (seconds shr 8))
    }

    suspend fun reset() {
        sendCommand(4)
    }

    suspend fun setEyeConfiguration(enabled: Boolean, mode: EyeDisplayMode = EyeDisplayMode.ANIMATION) {
        sendCommand(
            5, when (enabled) {
                true -> 1
                else -> 0
            }, mode.ordinal
        )
    }

    suspend fun setEyeValue(value: Int) {
        require(value in 0..255)
        sendCommand(6, value)
    }
}