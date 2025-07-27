package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class RealTimeClockDaemonTest {

    @Test
    fun testBasic() {
        val hardware = mockk<Hardware>()
        val d = RealTimeClockDaemon(hardware)

        coEvery { hardware.clockDisplay.setDisplay(any(), any(), any(), any()) } just Runs

        runBlocking {

            d.poll()
            d.poll()
            d.poll()

            coVerify(exactly = 1) { hardware.clockDisplay.setDisplay(any(), any(), any(), any()) }
        }
    }

    @Test
    fun testDisplayContent() {
        assertEquals(
            RealTimeClockDaemon.DisplayContent(21, 38, true, true),
            RealTimeClockDaemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 48))
        )

        assertEquals(
            RealTimeClockDaemon.DisplayContent(21, 38, false, false),
            RealTimeClockDaemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 49))
        )

        assertEquals(
            RealTimeClockDaemon.DisplayContent(21, 38, false, true),
            RealTimeClockDaemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 12))
        )

        assertEquals(
            RealTimeClockDaemon.DisplayContent(21, 38, false, false),
            RealTimeClockDaemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 13))
        )

        assertEquals(
            RealTimeClockDaemon.DisplayContent(21, 38, true, false),
            RealTimeClockDaemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 30))
        )

        assertEquals(
            RealTimeClockDaemon.DisplayContent(21, 38, false, false),
            RealTimeClockDaemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 31))
        )
    }
}