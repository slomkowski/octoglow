package eu.slomkowski.octoglow.octoglowd.demon


import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

interface Demon {
    fun createJobs(scope: CoroutineScope): List<Job>

    fun close(scope: CoroutineScope) {
        // do nothing
    }
}

/**
 * Daemons implement features which are long-running and periodical.
 */
abstract class PollingDemon(
    private val logger: KLogger,
    protected val pollingInterval: Duration,
    private val initialErrorBackoff: Duration = 1.seconds,
    private val maxErrorBackoff: Duration = 30.seconds,
) : Demon {

    companion object {
        /**
         * How much later than its polling interval a demon may wake up before we consider it starved.
         * Anything above this is a stall we did not cause ourselves - a GC pause or a saturated dispatcher.
         */
        private val latenessAllowance = 500.milliseconds
    }

    /**
     * This coroutine is polled with the interval defined for a daemon.
     */
    abstract suspend fun poll()

    override fun createJobs(scope: CoroutineScope): List<Job> {
        return listOf(scope.launch {
            var errorBackoff = initialErrorBackoff

            while (isActive) {
                try {
                    poll()
                    errorBackoff = initialErrorBackoff

                    // measuring the sleep itself, not the whole cycle: poll() is legitimately slow for
                    // some demons, but delay() overshooting means this coroutine could not be resumed.
                    // Several demons reporting lateness at once points at a JVM-wide pause.
                    val sleepStartedAt = TimeSource.Monotonic.markNow()
                    delay(pollingInterval)
                    val overshoot = sleepStartedAt.elapsedNow() - pollingInterval
                    if (overshoot > latenessAllowance) {
                        logger.warn { "${this@PollingDemon} resumed $overshoot later than its $pollingInterval interval." }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Exception caught in ${this@PollingDemon}." }
                    delay(errorBackoff)
                    errorBackoff = (errorBackoff * 2).coerceAtMost(maxErrorBackoff)
                }
            }
        })
    }

    override fun toString(): String = "[${this.javaClass.simpleName}]"
}
