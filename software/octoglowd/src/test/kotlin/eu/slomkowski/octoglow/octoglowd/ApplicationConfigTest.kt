package eu.slomkowski.octoglow.octoglowd

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class ApplicationConfigTest {
    @Test
    fun testLoad() {
        val config = Config.parse(Paths.get("config.json"))
        assertThat(config.i2cBus).isEqualTo(13)
    }
}