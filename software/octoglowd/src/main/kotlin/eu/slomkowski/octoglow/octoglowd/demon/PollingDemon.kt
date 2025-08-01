package eu.slomkowski.octoglow.octoglowd.demon


import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.*
import kotlin.time.Duration

abstract class Demon {
    abstract fun createJobs(scope: CoroutineScope): List<Job>
}

/**
 * Daemons implement features which are long-running and periodical.
 */
abstract class PollingDemon(
    private val logger: KLogger,
    protected val pollingInterval: Duration,
) : Demon() {

    /**
     * This coroutine is polled with the interval defined for a daemon.
     */
    abstract suspend fun poll()

    override fun createJobs(scope: CoroutineScope): List<Job> {
        return listOf(scope.launch {
            while (isActive) {
                try {
                    poll()
                    delay(pollingInterval.inWholeMilliseconds)
                } catch (e: Exception) {
                    logger.error(e) { "Exception caught in $coroutineContext." }
                    delay(5_000)
                }
            }
        })
    }

    override fun toString(): String = "[${this.javaClass.simpleName}]"
}
