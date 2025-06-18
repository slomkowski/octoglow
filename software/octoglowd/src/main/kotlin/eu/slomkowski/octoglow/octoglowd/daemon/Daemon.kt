package eu.slomkowski.octoglow.octoglowd.daemon


import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.*
import kotlin.time.Duration

/**
 * Daemons implement features which are long-running and periodical.
 */
abstract class Daemon(
    private val logger: KLogger,
    private val poolInterval: Duration,
) {

    /**
     * This coroutine is pooled with the interval defined for a daemon.
     */
    abstract suspend fun pool()

    suspend fun createJob(): Job = coroutineScope {
        delay(poolInterval.inWholeMilliseconds % 2000)

        logger.debug { "Creating repeating job." }

        launch {
            while (isActive) {
                try {
                    pool()
                    delay(poolInterval.inWholeMilliseconds)
                } catch (e: Exception) {
                    logger.error(e) { "Exception caught in $coroutineContext." }
                    delay(5_000)
                }
            }
        }
    }

    override fun toString(): String = "[${this.javaClass.simpleName}]"
}
