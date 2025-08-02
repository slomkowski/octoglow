@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime


class RealTimeClockDemonTest {

    @Test
    fun testBasic() {
        val hardware = mockk<Hardware>()
        val d = RealTimeClockDemon(hardware)

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
            RealTimeClockDemon.DisplayContent(21, 38, true, true),
            RealTimeClockDemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 48))
        )

        assertEquals(
            RealTimeClockDemon.DisplayContent(21, 38, false, false),
            RealTimeClockDemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 49))
        )

        assertEquals(
            RealTimeClockDemon.DisplayContent(21, 38, false, true),
            RealTimeClockDemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 12))
        )

        assertEquals(
            RealTimeClockDemon.DisplayContent(21, 38, false, false),
            RealTimeClockDemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 13))
        )

        assertEquals(
            RealTimeClockDemon.DisplayContent(21, 38, true, false),
            RealTimeClockDemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 30))
        )

        assertEquals(
            RealTimeClockDemon.DisplayContent(21, 38, false, false),
            RealTimeClockDemon.DisplayContent.ofTimestamp(LocalDateTime(2019, 9, 3, 21, 38, 31))
        )
    }
}