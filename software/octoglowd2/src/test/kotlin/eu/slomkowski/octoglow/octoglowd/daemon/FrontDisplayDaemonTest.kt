package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDaemon.Companion.updateViewIndex
import eu.slomkowski.octoglow.octoglowd.daemon.view.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.daemon.view.UpdateStatus
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonReport
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.mockk.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class FrontDisplayDaemonTest {

    companion object : KLogging()

    class TestView(name : String) : FrontDisplayView(name, Duration.ofSeconds(10), Duration.ofSeconds(1)) {

        override suspend fun poolStatusData(): UpdateStatus {
            logger.info { "Calll poolStatusData." }
            return UpdateStatus.FULL_SUCCESS
        }

        override suspend fun poolInstantData(): UpdateStatus {
            logger.info { "Calll poolInstantData." }
            return UpdateStatus.FULL_SUCCESS
        }

        override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) {
            logger.info { "Screen redrawn." }
       }

    }

    @Test
    fun testStateMachineSwitchView() {
        runBlocking {
            val hardware = mockk<Hardware>()

            coEvery {hardware.frontDisplay.clear()} just Runs

            coEvery { hardware.frontDisplay.getButtonReport() } returns ButtonReport(ButtonState.NO_CHANGE, 1)

            val v1 = mockk<FrontDisplayView>()
            val v2 = mockk<FrontDisplayView>()

            coEvery { v1.redrawDisplay(true, true) } just Runs
            coEvery { v2.redrawDisplay(true, true) } just Runs

            val d = FrontDisplayDaemon(coroutineContext, hardware, listOf(v1, v2))

            d.pool()

            d.pool()

            coVerify { v1.redrawDisplay(true, true) }
            coVerify { v2.redrawDisplay(true, true) }
        }
    }

    @Test
    fun testUpdateViewIndex() {
        assertEquals(3, updateViewIndex(2, 1, 5))
        assertEquals(0, updateViewIndex(2, 1, 3))
        assertEquals(1, updateViewIndex(2, -1, 3))
        assertEquals(0, updateViewIndex(2, -2, 3))
        assertEquals(2, updateViewIndex(2, -3, 3))
        assertEquals(1, updateViewIndex(2, -6, 5))
        assertEquals(33, updateViewIndex(0, -37, 35))
        assertEquals(0, updateViewIndex(0, -36, 2))
        assertEquals(1, updateViewIndex(0, -9, 2))
    }
}