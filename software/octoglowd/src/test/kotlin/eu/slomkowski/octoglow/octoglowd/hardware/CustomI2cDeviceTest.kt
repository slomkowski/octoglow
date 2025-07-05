package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.hardware.CustomI2cDevice.Companion.calculateCcittCrc8
import eu.slomkowski.octoglow.octoglowd.hardware.CustomI2cDevice.Companion.createCommandWithCrc
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CustomI2cDeviceTest {

    @Test
    fun testCalculateCrc8() {
        val buff = intArrayOf(251, 4, 2, 137, 20, 104, 132, 19)
        val result = calculateCcittCrc8(buff, 1..<buff.size)
        assertThat(result).isEqualTo(buff.first())
    }

    @Test
    fun testCreateCommandWithCrc() {
        val buff = intArrayOf(1, 49, 50, 51, 52)
        val result = createCommandWithCrc(*buff)
        assertThat(result).isEqualTo(intArrayOf(160, 1, 49, 50, 51, 52))
    }
}