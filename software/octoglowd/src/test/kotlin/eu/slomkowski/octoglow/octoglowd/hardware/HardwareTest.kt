package eu.slomkowski.octoglow.octoglowd.hardware

import com.thedeanda.lorem.LoremIpsum
import eu.slomkowski.octoglow.octoglowd.testConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class HardwareTest {

    companion object {
        private val logger = KotlinLogging.logger {}

        fun createRealHardware(): Hardware {
            val config = testConfig
            return Hardware(config)
        }
    }

    @Test
    @Tag("hardware")
    fun testBrightness() {
        runBlocking {
            createRealHardware().use { hardware ->
                hardware.frontDisplay.setStaticText(0, LoremIpsum.getInstance().getWords(20).take(39))
                hardware.clockDisplay.setDisplay(12, 34, upperDot = true, lowerDot = false)

                for (b in (0..5)) {
                    logger.info { "Setting brightness to $b." }
                    hardware.setBrightness(b)
                    delay(4_000)
                }

                delay(2000)
            }
        }
    }

    @Test
    fun testHandleException() {
        Hardware.handleI2cException(Exception("Native error while writing I2C buffer : errno 6")).also {
            assertThat(it.message).isEqualTo("native I2C transaction error: ENXIO: No such device or address")
        }

        Hardware.handleI2cException(Exception("Native error while writing I2C buffer : errno 32423")).also {
            assertThat(it.message).isEqualTo("native I2C transaction error: errno 32423")
        }
    }
}