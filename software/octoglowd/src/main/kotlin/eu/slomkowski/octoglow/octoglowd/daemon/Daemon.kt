package eu.slomkowski.octoglow.octoglowd.daemon


import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.handleException
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.*
import mu.KLogger
import kotlin.time.Duration

/**
 * Daemons implement features which are long-running and periodical.
 */
abstract class Daemon(
    private val config: Config,
    private val hardware: Hardware,
    private val logger: KLogger,
    private val poolInterval: Duration
) {

    /**
     * This coroutine is pooled with the interval defined for a daemon.
     */
    abstract suspend fun pool()

    suspend fun createJob(): Job = coroutineScope {
        delay(poolInterval.inWholeMilliseconds % 2000)

        logger.debug("Creating repeating job.")

        launch {
            while (isActive) {
                try {
                    pool()
                    delay(poolInterval.inWholeMilliseconds)
                } catch (e: Exception) {
                    handleException(config, logger, hardware, coroutineContext, e)
                    delay(5_000)
                }
            }
        }
    }

    protected val exceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
        runBlocking {
            handleException(config, logger, hardware, coroutineContext, throwable)
        }
    }

    override fun toString(): String = "[${this.javaClass.simpleName}]"
}
