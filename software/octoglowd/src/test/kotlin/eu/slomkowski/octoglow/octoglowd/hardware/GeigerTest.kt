package eu.slomkowski.octoglow.octoglowd.hardware

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


@ExtendWith(HardwareParameterResolver::class)
class GeigerTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Test
    fun testGeigerCounterStateParse() {
        assertEquals(
            GeigerCounterState(
                hasNewCycleStarted = false,
                hasCycleEverCompleted = false,
                numOfCountsInCurrentCycle = 23,
                numOfCountsInPreviousCycle = 32,
                currentCycleProgress = 50.seconds,
                cycleLength = 200.seconds,
            ),
            GeigerCounterState.parse(intArrayOf(0, 23, 0, 32, 0, 50, 0, 200, 0))
        )

        assertEquals(
            GeigerCounterState(
                hasNewCycleStarted = true,
                hasCycleEverCompleted = true,
                numOfCountsInCurrentCycle = 23,
                numOfCountsInPreviousCycle = 92,
                currentCycleProgress = 50.seconds,
                cycleLength = 5.minutes,
            ),
            GeigerCounterState.parse(intArrayOf(3, 23, 0, 92, 0, 50, 0, 44, 1))
        )

        assertFails {
            val invalid = intArrayOf(0, 255, 255, 255, 255, 255, 255, 255, 255)
            GeigerCounterState.parse(invalid)
        }
    }

    @Test
    fun testSetBrightnessTo1(hardware: Hardware): Unit = runBlocking {
        hardware.geiger.setBrightness(1) // 110
    }

    @Test
    fun testSetBrightnessTo2(hardware: Hardware): Unit = runBlocking {
        hardware.geiger.setBrightness(2) // 140
    }

    @Test
    fun testSetBrightnessTo3(hardware: Hardware): Unit = runBlocking {
        hardware.geiger.setBrightness(3) // 180
    }

    @Test
    fun testSetBrightnessTo4(hardware: Hardware): Unit = runBlocking {
        hardware.geiger.setBrightness(4) // 210
    }

    @Test
    fun testSetBrightnessTo5(hardware: Hardware): Unit = runBlocking {
        hardware.geiger.setBrightness(5) // 250
    }

    @Test
    fun testGeigerVoltage(hardware: Hardware): Unit = runBlocking {
        val voltages = mutableListOf<Double>()

        repeat(10) {
            val voltage = hardware.geiger.getDeviceState().geigerVoltage
            voltages.add(voltage)
            delay(100.milliseconds)
        }
        logger.info { "Measured voltages: $voltages" }
        assert(voltages.average() in 380.0..420.0)
    }

    @Test
    fun testSetBrightness(hardware: Hardware): Unit = runBlocking {
        for (i in 0..5) {
            logger.info { "Setting brightness to $i." }
            hardware.geiger.setBrightness(i)
            delay(10_000)
        }

        hardware.geiger.setBrightness(3)
    }

    @Test
    fun testGetCounterState(hardware: Hardware): Unit = runBlocking {
        //geiger.reset()

        repeat(50) {
            val state = hardware.geiger.getCounterState()
            logger.info { "State: $state." }
            delay(50)
        }
    }
    
    

    @Test
    fun testEyeVoltages(hardware: Hardware) : Unit = runBlocking {
        hardware.geiger.setEyeConfiguration(true)
        val voltages = listOf(110.0, 140.0, 180.0, 210.0, 250.0)

        voltages.forEachIndexed { index, voltage ->
            hardware.geiger.setBrightness(index + 1)
            delay(4.seconds)

            val voltageValues = (0..30).map {
                delay(50.milliseconds)
                hardware.geiger.getDeviceState().eyeVoltage
            }
            val valuesAvg = voltageValues.average()

            logger.info {
                "Voltage: $voltage V, avg measured: ${String.format("%.1f", valuesAvg)} V, diff: ${String.format("%.1f", valuesAvg - voltage)} V st dev: ${
                    String.format(
                        "%.1f",
                        voltageValues.standardDeviation()
                    )
                }."
            }
        }
    }

    @Test
    fun testGetDeviceState(hardware: Hardware): Unit = runBlocking {

//        hardware.geiger.reset()

        repeat(50) {
            val state = hardware.geiger.getDeviceState()
            logger.info { "State: $state." }
            delay(80)
        }
    }

    @Test
//    @Disabled("method used to enable eye manually")
    fun enableEye(hardware: Hardware) {
        setEye(hardware, true)
    }

    @Test
//    @Disabled("method used to disable eye manually")
    fun disableEye(hardware: Hardware) {
        setEye(hardware, false)
    }

    private fun setEye(hardware: Hardware, v: Boolean) = runBlocking {
        hardware.geiger.setEyeConfiguration(v)
    }

    @Test
//    @Disabled("this test should be run only by hand, because of heating cycle")
    fun testEye(hardware: Hardware): Unit = runBlocking {
        val geiger = hardware.geiger
        geiger.setEyeConfiguration(false)
        geiger.getDeviceState().apply {
            assertEquals(EyeInverterState.DISABLED, eyeState)
        }

        geiger.setEyeConfiguration(true, EyeDisplayMode.ANIMATION)
        geiger.getDeviceState().apply {
            assertEquals(EyeInverterState.HEATING_LIMITED, eyeState)
            assertEquals(EyeDisplayMode.ANIMATION, eyeAnimationState)
        }

        delay(1000)

        geiger.getDeviceState().apply {
            assertEquals(EyeInverterState.HEATING_LIMITED, eyeState)
            assertEquals(EyeDisplayMode.ANIMATION, eyeAnimationState)
        }

        delay(8000)

        geiger.getDeviceState().apply {
            assertEquals(EyeInverterState.HEATING_FULL, eyeState)
            assertEquals(EyeDisplayMode.ANIMATION, eyeAnimationState)
        }

        delay(5000)

        geiger.getDeviceState().apply {
            assertEquals(EyeInverterState.RUNNING, eyeState)
            assertEquals(EyeDisplayMode.ANIMATION, eyeAnimationState)
        }

        delay(2000)

        geiger.getDeviceState().also {
            logger.info { "State: $it." }
        }

        geiger.setEyeConfiguration(true, EyeDisplayMode.FIXED_VALUE)
        geiger.getDeviceState().apply {
            assertEquals(EyeInverterState.RUNNING, eyeState)
            assertEquals(EyeDisplayMode.FIXED_VALUE, eyeAnimationState)
        }
        for (i in 0..15) {
            geiger.setEyeValue(10 * i)
            delay(1000)
        }

        for (i in 0..5) {
            geiger.setBrightness(i)
            delay(1000)
        }

        geiger.setEyeConfiguration(true, EyeDisplayMode.ANIMATION)
        geiger.setBrightness(3)
        geiger.getDeviceState().apply {
            assertEquals(EyeInverterState.RUNNING, eyeState)
            assertEquals(EyeDisplayMode.ANIMATION, eyeAnimationState)
        }

        geiger.setEyeConfiguration(false)
        geiger.getDeviceState().apply {
            assertEquals(EyeInverterState.DISABLED, eyeState)
            assertEquals(EyeDisplayMode.ANIMATION, eyeAnimationState)
        }
    }

    private fun List<Double>.standardDeviation(): Double {
        val mean = average()
        return sqrt(sumOf { (it - mean) * (it - mean) } / size)
    }
}
