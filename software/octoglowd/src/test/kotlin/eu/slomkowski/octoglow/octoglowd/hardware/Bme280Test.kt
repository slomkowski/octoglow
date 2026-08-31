package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.hardware.Bme280.Companion.checkNot00andNotFF
import eu.slomkowski.octoglow.octoglowd.hardware.Bme280.Companion.signExtend
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
    fun testSignExtend() {
        // 8-bit signed (dig_H6 is a signed char)
        assertThat(0x00.signExtend(8)).isEqualTo(0)
        assertThat(0x7f.signExtend(8)).isEqualTo(127)
        assertThat(0x80.signExtend(8)).isEqualTo(-128)
        assertThat(0xff.signExtend(8)).isEqualTo(-1)
        assertThat(0xe6.signExtend(8)).isEqualTo(-26)

        // 12-bit signed (dig_H4 / dig_H5 are signed 12-bit)
        assertThat(0x000.signExtend(12)).isEqualTo(0)
        assertThat(0x7ff.signExtend(12)).isEqualTo(2047)
        assertThat(0x800.signExtend(12)).isEqualTo(-2048)
        assertThat(0xfff.signExtend(12)).isEqualTo(-1)
        assertThat(0x801.signExtend(12)).isEqualTo(-2047)
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