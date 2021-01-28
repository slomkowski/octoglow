package eu.slomkowski.octoglow.octoglowd.daemon

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.handleException
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogger
import java.time.Duration
import kotlin.coroutines.coroutineContext

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

    suspend fun startPooling() {
        for (t in ticker(
            poolInterval.toMillis(),
            initialDelayMillis = poolInterval.toMillis() % 2000,
            mode = TickerMode.FIXED_DELAY
        )) {
            try {
                pool()
            } catch (e: Exception) {
                handleException(config, logger, hardware, coroutineContext, e)
                delay(5_000)
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
