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
    fun testStateMachineBasics() {
        runBlocking {
            val hardware = mockk<Hardware>()
            val d = FrontDisplayDaemon(coroutineContext, hardware, listOf(TestView("T1"), TestView("T2")) )

            coEvery {hardware.frontDisplay.clear()} just Runs

            coEvery { hardware.frontDisplay.getButtonReport() } returns ButtonReport(ButtonState.NO_CHANGE, 1)

            // todo test
            d.pool()
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