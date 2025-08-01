package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.datacollectors.MeasurementReport
import eu.slomkowski.octoglow.octoglowd.getSegmentNumber
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KLogger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
abstract class FrontDisplayView2<StatusType, InstantType>(
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

    sealed class UpdateStatus {
        data object NoNewData : UpdateStatus()
        data class NewData(val newStatus: Any?) : UpdateStatus()
    }

    /**
     * @return true if the new data arrived and
     * todo może flow, który aktualizuje status??
     */
    abstract suspend fun onNewMeasurementReport(report: MeasurementReport,
                                                oldStatus: StatusType?): UpdateStatus

    abstract val preferredDisplayTime: Duration

    open suspend fun pollInstantData(now: Instant, oldInstant : InstantType?): UpdateStatus {
        error("should not be called if instant data interval not defined. otherwise, implement it")
    }

    open fun getMenus(): List<Menu> = emptyList()

    abstract suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: StatusType?,
        instant: InstantType?,
    )

    override fun toString(): String = "'$name'"

    protected suspend fun drawProgressBar(
        reportTimestamp: Instant?,
        now: Instant,
        period: Duration?,
    ) = coroutineScope {
        val fd = hardware.frontDisplay
        if (reportTimestamp != null && period != null) {
            val currentCycleDuration = now - reportTimestamp
            check(!currentCycleDuration.isNegative())
            launch { fd.setUpperBar(listOf(getSegmentNumber(currentCycleDuration, period))) }
        } else {
            launch { fd.setUpperBar(emptyList()) }
        }
    }
}
