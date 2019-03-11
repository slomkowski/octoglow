package eu.slomkowski.octoglow.octoglowd.daemon.view

import java.time.Duration

enum class UpdateStatus {
    NO_NEW_DATA,
    FULL_SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE
}

interface FrontDisplayView {

    suspend fun redrawDisplay(firstTime: Boolean)

    suspend fun poolStateUpdate(): UpdateStatus

    fun getPreferredPoolingInterval(): Duration

    val name: String
}