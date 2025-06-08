package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.hardware.HardwareTest
import eu.slomkowski.octoglow.octoglowd.jsonSerializer
import eu.slomkowski.octoglow.octoglowd.now
import eu.slomkowski.octoglow.octoglowd.readToString
import eu.slomkowski.octoglow.octoglowd.testConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class SimpleMonitorViewTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

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
        val url = testConfig.simplemonitor.url
        val user = testConfig.simplemonitor.user
        val password = testConfig.simplemonitor.password

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
    @Tag("hardware")
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
            val view = SimpleMonitorView(testConfig, hardware)
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