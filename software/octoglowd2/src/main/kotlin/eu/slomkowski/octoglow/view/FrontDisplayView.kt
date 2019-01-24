package eu.slomkowski.octoglow.view

import kotlinx.coroutines.Deferred
import java.time.Duration

interface FrontDisplayView {
    suspend fun redrawDisplay()

    suspend fun poolStateUpdate(): Deferred<Boolean>

    fun getPreferredPoolingInterval(): Duration
}