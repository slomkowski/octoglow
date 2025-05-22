package eu.slomkowski.octoglow.octoglowd.daemon


import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDaemon.Companion.updateViewIndex
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.UpdateStatus
import eu.slomkowski.octoglow.octoglowd.defaultTestConfig
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonReport
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds


class FrontDisplayDaemonTest {

    companion object : KLogging()

    class TestView(name: String) : FrontDisplayView(
        mockk(),
        name,
        10.seconds,
        1.seconds,
        7.seconds
    ) {

        override suspend fun poolStatusData(now: Instant): UpdateStatus {
            logger.info { "Call poolStatusData." }
            return UpdateStatus.FULL_SUCCESS
        }

        override suspend fun poolInstantData(now: Instant): UpdateStatus {
            logger.info { "Call poolInstantData." }
            return UpdateStatus.FULL_SUCCESS
        }

        override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) {
            logger.info { "Screen redrawn." }
        }

    }

    //todo better view tests

    @Test
    fun testStateMachineSwitchView() {
        runBlocking {
            val hardware = mockk<Hardware>()

            coEvery { hardware.frontDisplay.clear() } just Runs

            coEvery { hardware.frontDisplay.getButtonReport() } returns ButtonReport(ButtonState.NO_CHANGE, 1)

            val v1 = mockk<FrontDisplayView>()
            val v2 = mockk<FrontDisplayView>()

            coEvery { v1.getMenus() } returns listOf()
            coEvery { v2.getMenus() } returns listOf()

            coEvery { v1.redrawDisplay(true, true, any()) } just Runs
            coEvery { v2.redrawDisplay(true, true, any()) } just Runs

            val d = FrontDisplayDaemon(defaultTestConfig, coroutineContext, hardware, listOf(v1, v2), emptyList())

            d.pool()

            d.pool()

            coVerify { v1.redrawDisplay(true, true, any()) }
            coVerify { v2.redrawDisplay(true, true, any()) }
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