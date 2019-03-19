package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFails

@ExtendWith(I2CBusParameterResolver::class)
class GeigerTest {

    companion object : KLogging()

    @Test
    fun testGeigerCounterStateParse() {
        assertEquals(GeigerCounterState(false, false, 23,
                32, Duration.ofSeconds(50), Duration.ofSeconds(200)),
                GeigerCounterState.parse(I2CBuffer(9)
                        .set(0, 0)
                        .set(1, 23)
                        .set(2, 0)
                        .set(3, 32)
                        .set(4, 0)
                        .set(5, 50)
                        .set(6, 0)
                        .set(7, 200)
                        .set(8, 0)))

        assertEquals(GeigerCounterState(true, true, 23,
                92, Duration.ofSeconds(50), Duration.ofMinutes(5)),
                GeigerCounterState.parse(I2CBuffer(9)
                        .set(0, 3)
                        .set(1, 23)
                        .set(2, 0)
                        .set(3, 92)
                        .set(4, 0)
                        .set(5, 50)
                        .set(6, 0)
                        .set(7, 44)
                        .set(8, 1)))

        assertFails {
            val invalid = listOf(0, 255, 255, 255, 255, 255, 255, 255, 255).toI2CBuffer()
            GeigerCounterState.parse(invalid)
        }
    }

    @Test
    fun testSetBrightness(bus: I2CBus) {
        runBlocking {
            Geiger(coroutineContext, bus).use { geiger ->
                for (i in 0..5) {
                    logger.info { "Setting brightness to $i." }
                    geiger.setBrightness(i)
                    delay(10_000)
                }

                geiger.setBrightness(3)
            }
        }
    }

    @Test
    fun testGetCounterState(bus: I2CBus) {
        runBlocking {
            val geiger = Geiger(coroutineContext, bus)

            //geiger.reset()

            repeat(10) {
                val state = geiger.getCounterState()
                logger.info { "State: $state." }
                delay(50)
            }
        }
    }

    @Test
    fun testGetDeviceState(bus: I2CBus) {
        runBlocking {
            val geiger = Geiger(coroutineContext, bus)

            geiger.reset()

            repeat(10) {
                val state = geiger.getDeviceState()
                logger.info { "State: $state." }
                delay(80)
            }
        }
    }

    @Test
    @Disabled("method used to enable eye manually")
    fun enableEye(bus: I2CBus) {
        setEye(bus, true)
    }

    @Test
    @Disabled("method used to disable eye manually")
    fun disableEye(bus: I2CBus) {
        setEye(bus, false)
    }

    private fun setEye(bus: I2CBus, v: Boolean) = runBlocking {
        val geiger = Geiger(coroutineContext, bus)
        geiger.setEyeConfiguration(v)
    }

    @Test
    @Disabled("this test should be run only by hand, because of heating cycle")
    fun testEye(bus: I2CBus) {
        runBlocking {
            val geiger = Geiger(coroutineContext, bus)

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

            logger.info("State: {}", geiger.getDeviceState())

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
    }
}