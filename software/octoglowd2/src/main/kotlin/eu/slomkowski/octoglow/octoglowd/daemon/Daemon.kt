package eu.slomkowski.octoglow.octoglowd.daemon

import kotlinx.coroutines.channels.ticker
import java.time.Duration

/**
 * Daemons implement features which are long-running and periodical.
 */
abstract class Daemon(private val poolInterval: Duration) {

    /**
     * This coroutine is pooled with the interval defined for a daemon.
     */
    abstract suspend fun pool()

    suspend fun startPooling() {
        for (t in ticker(poolInterval.toMillis(), initialDelayMillis = poolInterval.toMillis() % 2000)) {
            pool()
        }
    }

    override fun toString(): String = "[${this.javaClass.simpleName}]"
}
