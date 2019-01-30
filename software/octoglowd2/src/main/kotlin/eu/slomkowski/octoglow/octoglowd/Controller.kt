package eu.slomkowski.octoglow.octoglowd

import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration

interface Controller {
    abstract suspend fun pool()

    abstract val poolInterval: Duration

    suspend fun startPooling() = coroutineScope {
        launch {
            for (t in ticker(poolInterval.toMillis(), initialDelayMillis = poolInterval.toMillis() % 2000)) {
                pool()
            }
        }
    }
}