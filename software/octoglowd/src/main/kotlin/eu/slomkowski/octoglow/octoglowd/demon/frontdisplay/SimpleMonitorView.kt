@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.abbreviate
import eu.slomkowski.octoglow.octoglowd.dataharvesters.SimplemonitorDataHarvester
import eu.slomkowski.octoglow.octoglowd.dataharvesters.SimplemonitorDataSnapshot
import eu.slomkowski.octoglow.octoglowd.formatJustHoursMinutes
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.toLocalTime
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class SimpleMonitorView(
    hardware: Hardware
) : FrontDisplayView<SimpleMonitorView.CurrentReport, Unit>(
    hardware,
    "SimpleMonitor",
    null,
    logger,
) {

    data class CurrentReport(
        val timestamp: kotlin.time.Instant,
        val cycleLength: Duration?,
        val data: SimplemonitorDataHarvester.SimpleMonitorJson?,
    )

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun preferredDisplayTime(status: CurrentReport?): Duration {
        val noFailedMonitors = status?.data?.monitors?.filterValues { it.status == SimplemonitorDataHarvester.MonitorStatus.FAIL }?.count() ?: 0
        return (1500.milliseconds * noFailedMonitors).coerceIn(5.seconds, 60.seconds)
    }

    override suspend fun onNewDataSnapshot(
        snapshot: Snapshot,
        oldStatus: CurrentReport?
    ): UpdateStatus {
        if (snapshot !is SimplemonitorDataSnapshot) {
            return UpdateStatus.NoNewData
        }

        return UpdateStatus.NewData(
            CurrentReport(
                snapshot.timestamp,
                snapshot.cycleLength,
                snapshot.data,
            )
        )
    }

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: kotlin.time.Instant,
        status: CurrentReport?,
        instant: Unit?,
    ): Unit = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatus) {
            logger.debug { "Redrawing SimpleMonitor status." }

            fun monitors(singleStatus: SimplemonitorDataHarvester.MonitorStatus) = status?.data?.monitors?.filterValues { it.status == singleStatus }
            val failedMonitors = monitors(SimplemonitorDataHarvester.MonitorStatus.FAIL)

            launch {
                val time = status?.data?.generated?.toLocalDateTime(TimeZone.currentSystemDefault())
                    ?.toLocalTime()
                    ?.formatJustHoursMinutes()
                    ?: "--:--"
                fd.setStaticText(0, "*$time")
            }

            launch {
                val okMonitorsCount = monitors(SimplemonitorDataHarvester.MonitorStatus.OK)?.size
                val skippedMonitorsCount = monitors(SimplemonitorDataHarvester.MonitorStatus.SKIPPED)?.size

                val allMonitorsCount = status?.data?.monitors?.size

                val upperText = "OK:${okMonitorsCount ?: "--"}+${
                    skippedMonitorsCount
                        ?: "--"
                }/${allMonitorsCount ?: "--"}"

                fd.setStaticText(20 - upperText.length, upperText)
            }

            launch {
                when (failedMonitors?.size) {
                    null -> fd.setStaticText(21, "SERVER COMM ERROR!")
                    0 -> fd.setStaticText(22, "All monitors OK")
                    else -> {
                        val failedText = "${failedMonitors.size} FAILED"
                        fd.setStaticText(20, failedText)
                        val scrollingText = failedMonitors.keys.joinToString(",").abbreviate(Slot.SLOT0.capacity)

                        fd.setScrollingText(
                            Slot.SLOT0,
                            21 + failedText.length,
                            19 - failedText.length,
                            scrollingText
                        )
                    }
                }
            }
        }

        drawProgressBar(status?.timestamp, now, status?.cycleLength)
    }
}