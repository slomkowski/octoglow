package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.DataSnapshot
import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.getSegmentNumber
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

sealed class UpdateStatus {
    data object NoNewData : UpdateStatus()
    data class NewData(val newStatus: Any?) : UpdateStatus()
}

@OptIn(ExperimentalTime::class)
abstract class FrontDisplayView<StatusType, InstantType>(
    val hardware: Hardware,
    val name: String,
    val pollInstantEvery: Duration?,
    private val logger: KLogger,
) {
    init {
        check(name.isNotBlank())
        if (pollInstantEvery != null) {
            check(pollInstantEvery.isPositive())
        }
    }

    abstract suspend fun onNewDataSnapshot(
        snapshot : Snapshot,
        oldStatus: StatusType?,
    ): UpdateStatus

    abstract fun preferredDisplayTime(status: StatusType?): Duration

    open suspend fun pollForNewInstantData(now: Instant, oldInstant: InstantType?): UpdateStatus {
        error("Should not be called if instant polling interval not defined. Otherwise, implement it.")
    }

    abstract suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: StatusType?,
        instant: InstantType?,
    )

    override fun toString(): String = "'$name'"

    protected suspend fun drawProgressBar(
        statusTimestamp: Instant?,
        now: Instant,
        period: Duration?,
    ) = coroutineScope {
        val fd = hardware.frontDisplay
        if (statusTimestamp != null && period != null) {
            val currentCycleDuration = now - statusTimestamp
            check(!currentCycleDuration.isNegative())
            launch { fd.setUpperBar(listOf(getSegmentNumber(currentCycleDuration, period))) }
        } else {
            launch { fd.setUpperBar(emptyList()) }
        }
    }
}
