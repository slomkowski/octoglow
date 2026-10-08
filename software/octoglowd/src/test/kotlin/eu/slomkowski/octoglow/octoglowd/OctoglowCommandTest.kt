package eu.slomkowski.octoglow.octoglowd

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.IncorrectOptionValueCount
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.core.parse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile

/**
 * Only invalid command lines are tested, valid ones would run the daemon or burn the firmware.
 */
class OctoglowCommandTest {

    @Test
    fun `rejects unknown device`(@TempDir dir: Path) {
        val hexFile = dir.resolve("firmware.hex").createFile()

        val e = assertThrows<BadParameterValue> {
            OctoglowCommand().parse(listOf("--burn-firmware", "toaster", hexFile.toString()))
        }
        assertThat(e.message).contains("unknown device 'toaster'").contains("supported: clock-display")
    }

    @Test
    fun `rejects the old device name`(@TempDir dir: Path) {
        val hexFile = dir.resolve("firmware.hex").createFile()

        assertThrows<BadParameterValue> {
            OctoglowCommand().parse(listOf("--burn-firmware", "clock", hexFile.toString()))
        }
    }

    @Test
    fun `rejects missing hex file`(@TempDir dir: Path) {
        val e = assertThrows<BadParameterValue> {
            OctoglowCommand().parse(listOf("--burn-firmware", "clock-display", dir.resolve("missing.hex").toString()))
        }
        assertThat(e.message).contains("doesn't exist")
    }

    @Test
    fun `requires both device and file`() {
        assertThrows<IncorrectOptionValueCount> {
            OctoglowCommand().parse(listOf("--burn-firmware", "clock-display"))
        }
    }

    @Test
    fun `rejects unknown option`() {
        assertThrows<NoSuchOption> {
            OctoglowCommand().parse(listOf("--foo"))
        }
    }

    @Test
    fun `help describes firmware burning`() {
        val help = OctoglowCommand().getFormattedHelp()
        assertThat(help).contains("--burn-firmware=<device hex_file>").contains("Supported devices: clock-display.")
    }
}
