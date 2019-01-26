package eu.slomkowski.octoglow.octoglowd.view

import kotlinx.coroutines.Deferred
import java.time.Duration

interface FrontDisplayView {
    suspend fun redrawDisplay()

    suspend fun poolStateUpdate(): Deferred<Boolean>

    fun getPreferredPoolingInterval(): Duration

    val name: String
}