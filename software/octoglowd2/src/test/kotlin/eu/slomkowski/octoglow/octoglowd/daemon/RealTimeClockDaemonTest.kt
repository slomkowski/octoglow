package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RealTimeClockDaemonTest {

    @Test
    fun testBasic() {
        val hardware = mockk<Hardware>()
        val d = RealTimeClockDaemon(hardware)

        coEvery { hardware.clockDisplay.setDisplay(any(), any(), any(), any()) } just Runs

        runBlocking {
            d.setClockDisplayContent(LocalDateTime.of(2019, 3, 30, 21, 38, 3))
            coVerify { hardware.clockDisplay.setDisplay(21, 38, false, true) }

            d.pool()
        }
    }
}