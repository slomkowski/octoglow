import eu.slomkowski.octoglow.octoglowd.hardware.Scd40.Companion.calculateCrc8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class CompanionTest {

    @Test
    fun `calculateCrc8 should return correct CRC for a value of 0x07e6`() {
        assertThat(calculateCrc8(0x07e6)).isEqualTo(0x48)
        assertThat(calculateCrc8(0xbeef)).isEqualTo(0x92)
        assertThat(calculateCrc8(0x0000)).isEqualTo(0x81)
        assertThat(calculateCrc8(0xf896)).isEqualTo(0x31)
        assertThat(calculateCrc8(0x9f07)).isEqualTo(0xc2)
        assertThat(calculateCrc8(0x4c02)).isEqualTo(0xde)
        assertThat(calculateCrc8(0xe307)).isEqualTo(0x4d)
    }
}