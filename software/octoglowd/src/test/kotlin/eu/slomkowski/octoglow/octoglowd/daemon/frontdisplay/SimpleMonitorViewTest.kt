package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.HardwareTest
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import mu.KLogging
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test


class SimpleMonitorViewTest {

    companion object : KLogging()

    @Test
    fun testDeserialization() {
        SimpleMonitorViewTest::class.java.getResourceAsStream("/simplemonitor-json/1.json").use {
            jsonSerializer.decodeFromString<SimpleMonitorView.SimpleMonitorJson>(it.readToString())
        }.apply {
            assertNotNull(this)

            assertEquals(SimpleMonitorView.MonitorStatus.OK, monitors["monitor4"]?.status)
            assertEquals(SimpleMonitorView.MonitorStatus.FAIL, monitors["monitor6"]?.status)
        }
    }

    @Test
    fun testGetLatestSimpleMonitorJson() {
        val url = testConfig[SimpleMonitorKey.url]
        val user = testConfig[SimpleMonitorKey.user]
        val password = testConfig[SimpleMonitorKey.password]

        logger.info { "SimpleMonitor access data user: $user, password: $password." }

        runBlocking {
            SimpleMonitorView.getLatestSimpleMonitorJson(url, user, password).apply {
                assertNotNull(this)
                logger.debug {
                    "${monitors.size} monitors defined, ${
                        monitors.filterValues { it.status == SimpleMonitorView.MonitorStatus.OK }.count()
                    } are OK."
                }
            }
        }
    }

    @Test
    fun testLongFailedMonitors() {
        val report = SimpleMonitorView.CurrentReport(
            Instant.parse("2023-06-19T20:39:18.387530Z"),
            SimpleMonitorView.SimpleMonitorJson(
                Instant.parse("2023-06-19T20:39:18.387530Z"),
                (1..10).map {
                    "failing-monitor-$it-${RandomStringUtils.randomAlphabetic(20)}" to SimpleMonitorView.Monitor(
                        SimpleMonitorView.MonitorStatus.FAIL,
                        0,
                        ""
                    )
                }.plus(
                    "ok-monitor" to SimpleMonitorView.Monitor(
                        SimpleMonitorView.MonitorStatus.OK,
                        0,
                        ""
                    )
                ).toMap()
            )
        )
        HardwareTest.createRealHardware().use { hardware ->
            val view = SimpleMonitorView(testConfig, mockk(), hardware)
            view.currentReport = report
            runBlocking {
                try {
                    view.redrawDisplay(redrawStatic = true, redrawStatus = true, now = now())
                } catch (e: Exception) {
                    logger.error(e) { "Exception during redraw." }
                } finally {
                    delay(10_000)
                }
            }
        }
    }
}