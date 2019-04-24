package eu.slomkowski.octoglow.octoglowd.daemon

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDaemon.Companion.updateViewIndex
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.UpdateStatus
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

    class TestView(name: String) : FrontDisplayView(mockk(),
            name,
            Duration.ofSeconds(10),
            Duration.ofSeconds(1),
            Duration.ofSeconds(7)) {

        override suspend fun poolStatusData(): UpdateStatus {
            logger.info { "Call poolStatusData." }
            return UpdateStatus.FULL_SUCCESS
        }

        override suspend fun poolInstantData(): UpdateStatus {
            logger.info { "Call poolInstantData." }
            return UpdateStatus.FULL_SUCCESS
        }

        override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) {
            logger.info { "Screen redrawn." }
        }

    }

    //todo better view tests

    @Test
    fun testStateMachineSwitchView() {
        val config = Config {
            addSpec(ConfKey)
        }

        runBlocking {
            val hardware = mockk<Hardware>()

            coEvery { hardware.frontDisplay.clear() } just Runs

            coEvery { hardware.frontDisplay.getButtonReport() } returns ButtonReport(ButtonState.NO_CHANGE, 1)

            val v1 = mockk<FrontDisplayView>()
            val v2 = mockk<FrontDisplayView>()

            coEvery { v1.getMenus() } returns listOf()
            coEvery { v2.getMenus() } returns listOf()

            coEvery { v1.redrawDisplay(true, true) } just Runs
            coEvery { v2.redrawDisplay(true, true) } just Runs

            val d = FrontDisplayDaemon(config, coroutineContext, hardware, listOf(v1, v2), emptyList())

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