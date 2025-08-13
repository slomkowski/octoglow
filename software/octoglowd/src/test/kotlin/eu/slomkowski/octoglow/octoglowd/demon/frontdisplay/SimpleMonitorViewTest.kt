@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.dataharvesters.SimplemonitorDataHarvester
import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.time.Duration.Companion.minutes
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
    }
}