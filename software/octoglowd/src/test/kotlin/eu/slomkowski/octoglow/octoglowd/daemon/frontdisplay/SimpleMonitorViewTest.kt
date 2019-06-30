package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.SimpleMonitorKey
import eu.slomkowski.octoglow.octoglowd.jacksonObjectMapper
import eu.slomkowski.octoglow.octoglowd.testConfig
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SimpleMonitorViewTest {

    companion object : KLogging()

    @Test
    fun testDeserialization() {
        SimpleMonitorViewTest::class.java.getResourceAsStream("/simplemonitor-json/1.json").use {
            jacksonObjectMapper.readValue(it, SimpleMonitorView.SimpleMonitorJson::class.java)
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
                logger.debug { "${monitors.size} monitors defined, ${monitors.filterValues { it.status == SimpleMonitorView.MonitorStatus.OK }.count()} are OK." }
            }
        }
    }
}