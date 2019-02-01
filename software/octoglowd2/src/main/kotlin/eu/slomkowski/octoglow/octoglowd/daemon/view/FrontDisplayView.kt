package eu.slomkowski.octoglow.octoglowd.daemon.view

import kotlinx.coroutines.Deferred
import java.time.Duration

enum class UpdateStatus {
    NO_NEW_DATA,
    FULL_SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE
}

interface FrontDisplayView {

    suspend fun redrawDisplay()

    suspend fun poolStateUpdateAsync(): Deferred<UpdateStatus>

    fun getPreferredPoolingInterval(): Duration

    val name: String
}