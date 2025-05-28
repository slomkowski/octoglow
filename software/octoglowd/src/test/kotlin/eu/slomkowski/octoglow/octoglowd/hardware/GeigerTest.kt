package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.toI2CBuffer
import io.dvlopt.linux.i2c.I2CBuffer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFails
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
                false, false, 23,
                32, 50.seconds, 200.seconds
            ),
            GeigerCounterState.parse(
                I2CBuffer(9)
                    .set(0, 0)
                    .set(1, 23)
                    .set(2, 0)
                    .set(3, 32)
                    .set(4, 0)
                    .set(5, 50)
                    .set(6, 0)
                    .set(7, 200)
                    .set(8, 0)
            )
        )

        assertEquals(
            GeigerCounterState(
                true, true, 23,
                92, 50.seconds, 5.minutes
            ),
            GeigerCounterState.parse(
                I2CBuffer(9)
                    .set(0, 3)
                    .set(1, 23)
                    .set(2, 0)
                    .set(3, 92)
                    .set(4, 0)
                    .set(5, 50)
                    .set(6, 0)
                    .set(7, 44)
                    .set(8, 1)
            )
        )

        assertFails {
            val invalid = listOf(0, 255, 255, 255, 255, 255, 255, 255, 255).toI2CBuffer()
            GeigerCounterState.parse(invalid)
        }
    }

    @Test
    fun testSetBrightness(hardware: Hardware) {
        runBlocking {
            Geiger(hardware).use { geiger ->
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
    fun testGetCounterState(hardware: Hardware) {
        runBlocking {
            val geiger = Geiger(hardware)

            //geiger.reset()

            repeat(10) {
                val state = geiger.getCounterState()
                logger.info { "State: $state." }
                delay(50)
            }
        }
    }

    @Test
    fun testGetDeviceState(hardware: Hardware) {
        runBlocking {
            val geiger = Geiger(hardware)

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
    fun enableEye(hardware: Hardware) {
        setEye(hardware, true)
    }

    @Test
    @Disabled("method used to disable eye manually")
    fun disableEye(hardware: Hardware) {
        setEye(hardware, false)
    }

    private fun setEye(hardware: Hardware, v: Boolean) = runBlocking {
        val geiger = Geiger(hardware)
        geiger.setEyeConfiguration(v)
    }

    @Test
    @Disabled("this test should be run only by hand, because of heating cycle")
    fun testEye(hardware: Hardware) {
        runBlocking {
            val geiger = Geiger(hardware)

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