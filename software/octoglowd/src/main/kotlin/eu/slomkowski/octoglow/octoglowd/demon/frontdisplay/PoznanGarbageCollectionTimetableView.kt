@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.dataharvesters.PoznanGarbageCollectionTimetableSnapshot
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.toLocalDateInCurrentTimeZone
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.*
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class PoznanGarbageCollectionTimetableView(
    hardware: Hardware,
) : FrontDisplayView<PoznanGarbageCollectionTimetableView.CurrentReport, Unit>(
    hardware,
    "Poznań garbage collection timetable",
    null,
    logger,
) {
    companion object {
        private val logger = KotlinLogging.logger {}

        private const val HOW_MANY_DAYS_INTO_THE_FUTURE = 7

        val dayAndMonthFormat = LocalDate.Format {
            day(Padding.NONE)
            char('.')
            monthNumber()
        }

        fun createNextTrashDayInfo(today: LocalDate, nextPickupDate: LocalDate?): String = (if (nextPickupDate == null) {
            "--------"
        } else {
            when (val noDaysDiff = today.daysUntil(nextPickupDate)) {
                0 -> "TODAY"
                1 -> "tomorrow"
                in 2..9 -> "in $noDaysDiff days"
                else -> "in >9days"
            }
        }).padEnd(9)
    }

    data class DayOperation(
        val date: LocalDate,
        val garbageTypes: List<String>,
    )

    data class CurrentReport(
        val timestamp: Instant,
        val cycleLength: Duration,
        val dayOperations: List<DayOperation>,
    )

    override fun preferredDisplayTime(status: CurrentReport?): Duration = (5 + 2 * (status?.dayOperations?.size ?: 0)).seconds

    override suspend fun onNewDataSnapshot(snapshot: Snapshot, oldStatus: CurrentReport?): UpdateStatus {
        if (snapshot !is PoznanGarbageCollectionTimetableSnapshot) {
            return UpdateStatus.NoNewData
        }

        val today = snapshot.timestamp.toLocalDateInCurrentTimeZone()

        val dayOperations = snapshot.timetable.getOrNull()?.groupBy({ it.first }, { it.second })
            ?.map { (date, types) -> DayOperation(date, types.distinct().sorted()) }
            ?.filter { it.date >= today && it.date <= today.plus(HOW_MANY_DAYS_INTO_THE_FUTURE, DateTimeUnit.DAY) }
            ?.sortedBy { it.date }

        if (dayOperations == null) {
            return UpdateStatus.NoNewData
        }

        return UpdateStatus.NewData(CurrentReport(snapshot.timestamp, snapshot.cycleLength, dayOperations))
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant, status: CurrentReport?, instant: Unit?) {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            fd.setStaticText(0, "Next trash")
        }

        if (redrawStatus) {
            val today = now.toLocalDateInCurrentTimeZone()
            val dayOperations = status?.dayOperations?.filter { it.date >= today }
            val nextPickupDate = dayOperations?.minBy { it.date }?.date
            fd.setStaticText(11, createNextTrashDayInfo(today, nextPickupDate))

            if (dayOperations == null) {
                fd.setStaticText(21, "NO DATA DOWNLOADED")
            } else {
                if (dayOperations.isEmpty()) {
                    fd.setScrollingText(Slot.SLOT0, 20, 20, "no trash collection in the next $HOW_MANY_DAYS_INTO_THE_FUTURE days")
                } else {
                    val text = dayOperations.joinToString("  ") { dayOperation ->
                        dayOperation.date.format(dayAndMonthFormat) + ": " + dayOperation.garbageTypes.joinToString(
                            ","
                        ) {
                            when (dayOperation.date) {
                                nextPickupDate -> it.uppercase()
                                else -> it.lowercase()
                            }
                        }
                    }
                    fd.setScrollingText(Slot.SLOT0, 20, 20, text)
                }
            }
        }

        drawProgressBar(status?.timestamp, now, status?.cycleLength)
    }
}