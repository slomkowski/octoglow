package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.hardware.Bme280.Companion.checkNot00andNotFF
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertFails
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
            checkNot00andNotFF(intArrayOf(0, 0, 0))
        }

        assertFails {
            checkNot00andNotFF(intArrayOf(255, 255, 255))
        }

        checkNot00andNotFF(intArrayOf(0xff, 0xff, 0))
    }

    @Test
    fun testInitAndRead(hardware: Hardware): Unit = runBlocking {
        delay(1_000)

        repeat(15) {
            val report = hardware.bme280.readReport()
            logger.info { "Report: $report." }
            delay(500)
        }
    }
}