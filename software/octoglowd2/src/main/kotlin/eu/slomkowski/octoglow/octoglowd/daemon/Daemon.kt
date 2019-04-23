package eu.slomkowski.octoglow.octoglowd.daemon

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.SleepKey
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.isSleeping
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.delay
import mu.KLogger
import java.time.Duration
import java.time.LocalTime

/**
 * Daemons implement features which are long-running and periodical.
 */
abstract class Daemon(
        private val config: Config,
        private val hardware: Hardware,
        private val logger: KLogger,
        private val poolInterval: Duration) {

    /**
     * This coroutine is pooled with the interval defined for a daemon.
     */
    abstract suspend fun pool()

    suspend fun startPooling() {
        for (t in ticker(poolInterval.toMillis(), initialDelayMillis = poolInterval.toMillis() % 2000)) {
            try {
                pool()
            } catch (e: Exception) {
                if (config[ConfKey.ringAtError]) {
                    val sleeping = isSleeping(config[SleepKey.startAt], config[SleepKey.duration], LocalTime.now())
                    if (sleeping) {
                        logger.error(e) { "Not ringing because of sleep time;" }
                    } else {
                        logger.error(e) { "Demon error, ringing a bell;" }
                        try {
                            hardware.clockDisplay.ringBell(Duration.ofMillis(100))
                        } catch (ringException: Exception) {
                            logger.error(ringException) { "Cannot ring a bell, perhaps I2C error;" }
                        }
                    }
                } else {
                    logger.error(e) { "Ringing at error disabled, only logging stack trace;" }
                }

                delay(5_000)
            }
        }
    }

    override fun toString(): String = "[${this.javaClass.simpleName}]"
}
