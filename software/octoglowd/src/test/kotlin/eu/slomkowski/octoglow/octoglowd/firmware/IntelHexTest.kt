package eu.slomkowski.octoglow.octoglowd.firmware

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IntelHexTest {

    @Test
    fun `parses data records`() {
        // beginning of octoglow-clock-display.hex
        val image = IntelHex.parse(
            listOf(
                ":1000000030C0F9C149C048C047C07DC245C0DEC24A",
                ":04001000FBC242C02D",
                ":00000001FF",
            )
        )

        assertThat(image.endAddress).isEqualTo(0x14)
        assertThat(image[0]).isEqualTo(0x30)
        assertThat(image[1]).isEqualTo(0xc0)
        assertThat(image[0x13]).isEqualTo(0xc0)
        assertThat(image[0x14]).isEqualTo(0xff)
        assertThat(image.slice(0x12, 4)).containsExactly(0x42, 0xc0, 0xff, 0xff)
    }

    @Test
    fun `applies extended linear address`() {
        val image = IntelHex.parse(
            listOf(
                ":020000040001F9",
                ":01001000559A",
                ":00000001FF",
            )
        )

        assertThat(image.endAddress).isEqualTo(0x10011)
        assertThat(image[0x10010]).isEqualTo(0x55)
    }

    @Test
    fun `rejects invalid checksum`() {
        val e = assertThrows<IllegalArgumentException> {
            IntelHex.parse(listOf(":1000000030C0F9C149C048C047C07DC245C0DEC24B", ":00000001FF"))
        }
        assertThat(e.message).contains("line 1").contains("checksum")
    }

    @Test
    fun `rejects missing end of file`() {
        assertThrows<IllegalArgumentException> {
            IntelHex.parse(listOf(":04001000FBC242C02D"))
        }
    }
}
