package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.now
import eu.slomkowski.octoglow.octoglowd.testConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TodoistViewTest {

    private val logger = KotlinLogging.logger {}

    @Test
    fun testListening() = runBlocking {
        val view = TodoistView(testConfig, mockk<Hardware>())

        repeat(10) {
            view.poolStatusData(now())
            delay(5.seconds)
            //todo some tests
        }
    }
}