@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package eu.slomkowski.octoglow.octoglowd.demon


import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.defaultTestConfig
import eu.slomkowski.octoglow.octoglowd.demon.FrontDisplayDemon.Companion.updateViewIndex
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.UpdateStatus
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonReport
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class FrontDisplayDemonTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    class TestView(name: String) : FrontDisplayView<Any, Any>(
        mockk(),
        name,
        1.seconds,
        logger,
    ) {
        override fun preferredDisplayTime(status: Any?) = 7.seconds

        override suspend fun pollForNewInstantData(now: kotlin.time.Instant, oldInstant: Any?): UpdateStatus {
            logger.info { "Call poolInstantData." }
            return UpdateStatus.NewData(Unit)
        }

        override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: kotlin.time.Instant, status: Any?, instant: Any?) {
            logger.info { "Screen redrawn." }
        }

        override suspend fun onNewDataSnapshot(snapshot: Snapshot, oldStatus: Any?): UpdateStatus {
            TODO("Not yet implemented")
        }

    }

    //todo better view tests

    @Test
    fun testStateMachineSwitchView() = runTest {
        val hardware = mockk<Hardware>()
        val realTimeClockDemon = mockk<RealTimeClockDemon>()

        coEvery { hardware.frontDisplay.clear() } just Runs

        coEvery { hardware.frontDisplay.getButtonReport() } returns ButtonReport(ButtonState.NO_CHANGE, 1)

        coEvery { realTimeClockDemon.setFrontDisplayViewNumber(any(), any()) } just Runs

        val v1 = mockk<FrontDisplayView<Any, Any>>()
        val v2 = mockk<FrontDisplayView<Any, Any>>()

        coEvery { v1.redrawDisplay(true, true, any(), any(), any()) } just Runs
        coEvery { v2.redrawDisplay(true, true, any(), any(), any()) } just Runs

        val d = FrontDisplayDemon(defaultTestConfig, backgroundScope, hardware, listOf(v1, v2), emptyList(), mockk(), mockk(), realTimeClockDemon)

        // poll() no longer draws anything itself, it only queues the request - the dedicated job has
        // to be running, and has to drain between the two polls or the second request would simply
        // supersede the first
        d.createRedrawJob(backgroundScope)

        d.poll()
        runCurrent()

        d.poll()
        runCurrent()

        coVerify { v1.redrawDisplay(true, true, any(), any(), any()) }
        coVerify { v2.redrawDisplay(true, true, any(), any(), any()) }
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