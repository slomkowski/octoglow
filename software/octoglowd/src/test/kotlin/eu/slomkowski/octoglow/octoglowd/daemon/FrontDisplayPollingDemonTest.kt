package eu.slomkowski.octoglow.octoglowd.daemon


import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDemon2.Companion.updateViewIndex
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.FrontDisplayView2
import eu.slomkowski.octoglow.octoglowd.datacollectors.MeasurementReport
import eu.slomkowski.octoglow.octoglowd.defaultTestConfig
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonReport
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class FrontDisplayPollingDemonTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    class TestView(name: String) : FrontDisplayView2<Any, Any>(
        mockk(),
        name,
        1.seconds,
        logger,
    ) {
        override fun preferredDisplayTime(status: Any?) = 7.seconds

        override suspend fun pollInstantData(now: kotlin.time.Instant, oldInstant: Any?): UpdateStatus {
            logger.info { "Call poolInstantData." }
            return UpdateStatus.NewData(Unit)
        }

        override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: kotlin.time.Instant, status: Any?, instant: Any?) {
            logger.info { "Screen redrawn." }
        }

        override suspend fun onNewMeasurementReport(report: MeasurementReport, oldStatus: Any?): UpdateStatus {
            TODO("Not yet implemented")
        }

    }

    //todo better view tests

    @Test
    fun testStateMachineSwitchView() {
        runBlocking {
            coroutineScope {
                val hardware = mockk<Hardware>()

                coEvery { hardware.frontDisplay.clear() } just Runs

                coEvery { hardware.frontDisplay.getButtonReport() } returns ButtonReport(ButtonState.NO_CHANGE, 1)

                val v1 = mockk<FrontDisplayView2<Any, Any>>()
                val v2 = mockk<FrontDisplayView2<Any, Any>>()

                coEvery { v1.getMenus() } returns listOf()
                coEvery { v2.getMenus() } returns listOf()

                coEvery { v1.redrawDisplay(true, true, any(), any(), any()) } just Runs
                coEvery { v2.redrawDisplay(true, true, any(), any(), any()) } just Runs

                val d = FrontDisplayDemon2(defaultTestConfig, this, hardware, listOf(v1, v2), emptyList(), mockk(), mockk())

                d.poll()

                d.poll()

                coVerify { v1.redrawDisplay(true, true, any(), any(), any()) }
                coVerify { v2.redrawDisplay(true, true, any(), any(), any()) }
            }
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