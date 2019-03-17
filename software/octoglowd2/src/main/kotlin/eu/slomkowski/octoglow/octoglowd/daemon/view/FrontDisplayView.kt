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
abstract class FrontDisplayView(
        val name: String,
        val preferredStatusPoolingInterval: Duration,
        val preferredInstantPoolingInterval: Duration) {

    init {
        check(name.isNotBlank())
        check(preferredStatusPoolingInterval > Duration.ZERO)
        check(preferredInstantPoolingInterval > Duration.ZERO)
        check(preferredStatusPoolingInterval > preferredInstantPoolingInterval)
    }

    abstract suspend fun poolStatusData(): UpdateStatus

    abstract suspend fun poolInstantData(): UpdateStatus

    abstract suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean)
}