package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.hardware.Bme280.Companion.checkNot00andNotFF
import io.dvlopt.linux.i2c.I2CBuffer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExtendWith(HardwareParameterResolver::class)
class Bme280Test {
    private val logger = KotlinLogging.logger {}

    @Test
    fun testGetMeanSeaLevelPressure() {
        assertThat(Bme280measurements(22.0, 0.0, 1017.9).getMeanSeaLevelPressure(2.0))
            .isCloseTo(1018.14, Percentage.withPercentage(1.0))
        assertThat(Bme280measurements(22.0, 0.0, 1017.9).getMeanSeaLevelPressure(79.0)).isCloseTo(1027.25, Percentage.withPercentage(1.0))
    }

    @Test
    fun testCheckNot00andNotFF() {
        assertFails {
            checkNot00andNotFF(I2CBuffer(3).set(0, 0).set(1, 0).set(2, 0))
        }

        assertFails {
            checkNot00andNotFF(I2CBuffer(3).set(0, 255).set(1, 255).set(2, 255))
        }

        checkNot00andNotFF(
            I2CBuffer(3)
                .set(0, 0xff)
                .set(1, 0xff)
        )
    }

    @Test
    fun testInitAndRead(hardware: Hardware): Unit = runBlocking {
        val bme280 = Bme280(hardware)
        assertNotNull(bme280)
        bme280.initDevice()

        delay(1_000)

        repeat(15) {
            val report = bme280.readReport()
            logger.info { "Report: $report." }
            delay(500)
        }

        bme280.closeDevice()
    }
}