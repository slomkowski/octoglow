@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.DataSnapshotBus
import eu.slomkowski.octoglow.octoglowd.testConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class TodoistDataHarvesterTest {
    private val logger = KotlinLogging.logger {}

    @Test
    fun testListening() = runBlocking {
        val dataSnapshotBus = mockk<DataSnapshotBus>(relaxed = true)
        val harvester = TodoistDataHarvester(testConfig, dataSnapshotBus)

        repeat(10) {
            harvester.pollForNewData(Clock.System.now())
            delay(5.seconds)
            //todo some tests
        }
    }
}