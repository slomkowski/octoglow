package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.dataharvesters.SimplemonitorDataHarvester.Companion.getLatestSimpleMonitorJson
import eu.slomkowski.octoglow.octoglowd.jsonSerializer
import eu.slomkowski.octoglow.octoglowd.readToString
import eu.slomkowski.octoglow.octoglowd.testConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class SimplemonitorDataHarvesterTest {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Test
    fun testDeserialization() {
        SimplemonitorDataHarvesterTest::class.java.getResourceAsStream("/simplemonitor-json/1.json").use {
            jsonSerializer.decodeFromString<SimplemonitorDataHarvester.SimpleMonitorJson>(it.readToString())
        }.apply {
            assertNotNull(this)

            assertEquals(SimplemonitorDataHarvester.MonitorStatus.OK, monitors["monitor4"]?.status)
            assertEquals(SimplemonitorDataHarvester.MonitorStatus.FAIL, monitors["monitor6"]?.status)
        }
    }

    @Test
    fun testGetLatestSimpleMonitorJson() {
        val url = testConfig.simplemonitor.url
        val user = testConfig.simplemonitor.user
        val password = testConfig.simplemonitor.password

        logger.info { "SimpleMonitor access data user: $user, password: $password." }

        runBlocking {
            getLatestSimpleMonitorJson(url, user, password).apply {
                assertNotNull(this)
                logger.debug {
                    "${monitors.size} monitors defined, ${
                        monitors.filterValues { it.status == SimplemonitorDataHarvester.MonitorStatus.OK }.count()
                    } are OK."
                }
            }
        }
    }
}