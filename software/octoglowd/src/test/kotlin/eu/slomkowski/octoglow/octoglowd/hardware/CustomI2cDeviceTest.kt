package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.hardware.CustomI2cDevice.Companion.calculateCcittCrc8
import eu.slomkowski.octoglow.octoglowd.hardware.CustomI2cDevice.Companion.createCommandWithCrc
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CustomI2cDeviceTest {

    private val logger = KotlinLogging.logger {}

    @Test
    fun testCalculateCrc8() {
        val buff = intArrayOf(251, 4, 2, 137, 20, 104, 132, 19)
        val result = calculateCcittCrc8(buff, 1..<buff.size)
        assertThat(result).isEqualTo(buff.first())
    }

    @Test
    fun `calculate several arrays`() {
        listOf(
            7 to intArrayOf(1),
            14 to intArrayOf(2),
            9 to intArrayOf(3),
            28 to intArrayOf(4),
            27 to intArrayOf(5),
            18 to intArrayOf(6),
            21 to intArrayOf(7),
            56 to intArrayOf(8),
            63 to intArrayOf(9),
            213 to intArrayOf(5, 1, 0),
            199 to intArrayOf(5, 0, 1),
            24 to intArrayOf(6, 123),
            90 to intArrayOf(6, 12),
            76 to intArrayOf(3, 0x12, 0x34),
            41 to intArrayOf(6, 4, 0),
            242 to intArrayOf(1, 0x78, 0x56, 25, 0, 1, 0x34, 0x12, 7),
            188 to intArrayOf(1, 52, 0, 0xcf, 0x04, 0, 0, 0x2c, 0x1),
            252 to intArrayOf(2, 1, 0, 0, 0, 0, 0, 0, 0x2c, 0x1),
            133 to intArrayOf(2, 0, 0, 0, 0, 0, 0, 0, 0x2c, 0x1),
        ).forEach { (checksum, array) ->
            val result = calculateCcittCrc8(array, array.indices)
            logger.info { "Checksum: $result for array: ${array.contentToString()}." }
            assertThat(result).isEqualTo(checksum)
        }
    }

    @Test
    fun testCreateCommandWithCrc() {
        val buff = intArrayOf(1, 49, 50, 51, 52)
        val result = createCommandWithCrc(*buff)
        assertThat(result).isEqualTo(intArrayOf(160, 1, 49, 50, 51, 52))
    }
}