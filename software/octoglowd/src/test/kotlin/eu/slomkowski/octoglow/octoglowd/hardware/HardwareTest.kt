package eu.slomkowski.octoglow.octoglowd.hardware

import com.thedeanda.lorem.LoremIpsum
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@ExtendWith(HardwareParameterResolver::class)
@OptIn(ExperimentalTime::class)
class HardwareTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private suspend fun testBrightness(hardware: Hardware, b: Int) {
        setExampleText(hardware)
        logger.info { "Setting brightness to $b." }
        hardware.setBrightness(b)
    }

    private suspend fun setExampleText(hardware: Hardware) {
        hardware.frontDisplay.setStaticText(0, LoremIpsum.getInstance().getWords(20).take(39))
        hardware.clockDisplay.setDisplay(12, 34, upperDot = true, lowerDot = false)
    }

    @Test
    fun setBrightnessTo1(hardware: Hardware): Unit = runBlocking {
        testBrightness(hardware, 1)
    }

    @Test
    fun setBrightnessTo2(hardware: Hardware): Unit = runBlocking {
        testBrightness(hardware, 2)
    }

    @Test
    fun setBrightnessTo3(hardware: Hardware): Unit = runBlocking {
        testBrightness(hardware, 3)
    }

    @Test
    fun setBrightnessTo4(hardware: Hardware): Unit = runBlocking {
        testBrightness(hardware, 4)
    }

    @Test
    fun setBrightnessTo5(hardware: Hardware): Unit = runBlocking {
        testBrightness(hardware, 5)
    }

    @Test
    fun testBrightness(hardware: Hardware): Unit = runBlocking {
        setExampleText(hardware)

        for (b in 0..5) {
            logger.info { "Setting brightness to $b." }
            hardware.setBrightness(b)
            delay(2.seconds)
        }
    }

    @Test
    fun testHandleException() {
        HardwareReal.handleI2cException(Exception("Native error while writing I2C buffer : errno 6")).also {
            assertThat(it.message).isEqualTo("native I2C transaction error: ENXIO: No such device or address")
        }

        HardwareReal.handleI2cException(Exception("Native error while writing I2C buffer : errno 32423")).also {
            assertThat(it.message).isEqualTo("native I2C transaction error: errno 32423")
        }
    }
}