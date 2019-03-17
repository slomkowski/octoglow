package eu.slomkowski.octoglow.octoglowd.daemon.view

import java.time.Duration

enum class UpdateStatus {
    NO_NEW_DATA,
    FULL_SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE
}

/**
 * There are two kinds of data:
 * status - values provided by the view, updatable usually once a minute or so.
 * instant - exact state of the processing on the device, updatable once every several seconds.
 */
interface FrontDisplayView {

    /**
     * Human-readable name of the daemon. Visible mainly in logs.
     */
    val name: String

    val preferredStatusPoolingInterval: Duration

    val preferredInstantPoolingInterval: Duration

    suspend fun poolStatusData(): UpdateStatus

    suspend fun poolInstantData(): UpdateStatus

    suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean)
}