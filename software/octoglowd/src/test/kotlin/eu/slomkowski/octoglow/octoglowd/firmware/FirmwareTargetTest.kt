package eu.slomkowski.octoglow.octoglowd.firmware

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.io.path.isDirectory

class FirmwareTargetTest {

    /**
     * Tests run in software/octoglowd.
     */
    private val firmwareDirectory = Paths.get("../../firmware").toAbsolutePath().normalize()

    @Test
    fun `command line names are firmware directories`() {
        assertThat(firmwareDirectory.isDirectory()).describedAs("$firmwareDirectory").isTrue()

        FirmwareTarget.entries.forEach {
            assertThat(firmwareDirectory.resolve(it.commandLineName).isDirectory())
                .describedAs("firmware/${it.commandLineName} for $it")
                .isTrue()
        }
    }

    @Test
    fun `finds target by command line name`() {
        assertThat(FirmwareTarget.fromCommandLineName("clock-display")).isEqualTo(FirmwareTarget.CLOCK_DISPLAY)
        assertThat(FirmwareTarget.fromCommandLineName("clock")).isNull()
    }
}
