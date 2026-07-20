@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.dataharvesters.SimplemonitorDataHarvester
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SimpleMonitorViewTest {

    @Test
    fun testLongFailedMonitors() {
        val now = Instant.parse("2023-06-19T20:40:00Z")

        val report = SimpleMonitorView.CurrentReport(
            Instant.parse("2023-06-19T20:39:18.387530Z"),
            3.minutes,
            SimplemonitorDataHarvester.SimpleMonitorJson(
                Instant.parse("2023-06-19T20:39:18.387530Z"),
                (1..10).map {
                    "failing-monitor-$it-${UUID.randomUUID()}" to SimplemonitorDataHarvester.Monitor(
                        SimplemonitorDataHarvester.MonitorStatus.FAIL,
                        0,
                        ""
                    )
                }.plus(
                    "ok-monitor" to SimplemonitorDataHarvester.Monitor(
                        SimplemonitorDataHarvester.MonitorStatus.OK,
                        0,
                        ""
                    )
                ).toMap()
            )
        )
        val hardwareMock = HardwareMock()
        val view = SimpleMonitorView(hardwareMock)

        runBlocking {
            view.redrawDisplay(redrawStatic = true, redrawStatus = true, now = now, report, null)
        }

        println(hardwareMock.frontDisplay.renderDisplayContent())
        assertThat(hardwareMock.frontDisplay.line1content).startsWith("*").endsWith("     OK:1+0/11")
        assertThat(hardwareMock.frontDisplay.line2content).isEqualTo("10 FAILED ##########")
        assertThat(hardwareMock.frontDisplay.scrollingTextContent[Slot.SLOT0]).isNotNull()
    }

    @Test
    fun testPreferredDisplayTime() {
        val view = SimpleMonitorView(HardwareMock())
        val now = Clock.System.now()

        fun makeReport(failCount: Int): SimpleMonitorView.CurrentReport {
            val monitors = (0..<failCount).associate {
                "fail-$it" to SimplemonitorDataHarvester.Monitor(SimplemonitorDataHarvester.MonitorStatus.FAIL, 0, "")
            }
            return SimpleMonitorView.CurrentReport(
                now, 5.minutes,
                SimplemonitorDataHarvester.SimpleMonitorJson(now, monitors)
            )
        }

        assertThat(view.preferredDisplayTime(null)).isEqualTo(5.seconds)
        assertThat(view.preferredDisplayTime(makeReport(0))).isEqualTo(5.seconds)
        assertThat(view.preferredDisplayTime(makeReport(3))).isEqualTo(5.seconds)   // 4.5 s → clamped to min
        assertThat(view.preferredDisplayTime(makeReport(4))).isEqualTo(6.seconds)
        assertThat(view.preferredDisplayTime(makeReport(10))).isEqualTo(15.seconds)
        assertThat(view.preferredDisplayTime(makeReport(40))).isEqualTo(60.seconds)
        assertThat(view.preferredDisplayTime(makeReport(41))).isEqualTo(60.seconds) // clamped to max
    }
}