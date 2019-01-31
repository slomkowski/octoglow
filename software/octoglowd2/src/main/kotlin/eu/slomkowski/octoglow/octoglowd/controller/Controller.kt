package eu.slomkowski.octoglow.octoglowd.controller

import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration

abstract class Controller(private val poolInterval: Duration) {
    abstract suspend fun pool()

    suspend fun startPooling() = coroutineScope {
        launch {
            for (t in ticker(poolInterval.toMillis(), initialDelayMillis = poolInterval.toMillis() % 2000)) {
                pool()
            }
        }
    }
}