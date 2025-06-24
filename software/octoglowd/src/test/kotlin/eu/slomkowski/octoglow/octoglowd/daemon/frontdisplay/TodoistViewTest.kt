package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.testConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class TodoistViewTest {

    private val logger = KotlinLogging.logger {}

    @Test
    fun testFetchPendingTasksForToday(): Unit = runBlocking {
        val number= TodoistView.fetchPendingTasksForToday(testConfig.todoist.apiKey)
        logger.info { "number=$number" }
    }

    @Test
    fun testListening() = runBlocking {
        TodoistView.listenToChanges(testConfig.todoist.apiKey)
    }
}