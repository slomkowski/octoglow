package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class RealTimeClockDaemonTest {

    @Test
    fun testBasic() {
        val hardware = mockk<Hardware>()
        val d = RealTimeClockDaemon(hardware)

        coEvery { hardware.clockDisplay.setDisplay(any(), any(), any(), any()) } just Runs

        runBlocking {

            d.pool()
            d.pool()
            d.pool()

            coVerify(exactly = 1) { hardware.clockDisplay.setDisplay(any(), any(), any(), any()) }
        }
    }

    @Test
    fun testDisplayContent() {
        assertEquals(RealTimeClockDaemon.DisplayContent(21, 38, true, true),
                RealTimeClockDaemon.DisplayContent(LocalDateTime.of(2019, 9, 3, 21, 38, 48)))

        assertEquals(RealTimeClockDaemon.DisplayContent(21, 38, false, true),
                RealTimeClockDaemon.DisplayContent(LocalDateTime.of(2019, 9, 3, 21, 38, 12)))

        assertEquals(RealTimeClockDaemon.DisplayContent(21, 38, true, false),
                RealTimeClockDaemon.DisplayContent(LocalDateTime.of(2019, 9, 3, 21, 38, 30)))
    }
}