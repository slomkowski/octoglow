package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.calendar.calculatePolishShoppingSundays
import eu.slomkowski.octoglow.octoglowd.center
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.toLocalDateInCurrentTimeZone
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@OptIn(ExperimentalTime::class)
class ShoppingSundayView(
    private val config: Config,
    hardware: Hardware,
) : FrontDisplayView<LocalDate, Unit>(
    hardware,
    "Shopping Sunday",
    null,
    logger,
) {
    override fun preferredDisplayTime(status: LocalDate?) = 15.seconds

    companion object {
        private val logger = KotlinLogging.logger {}

        private val shortMonthNames = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )

        fun findNextShoppingSunday(today: LocalDate): LocalDate? {
            val nextThisYear = calculatePolishShoppingSundays(today.year).firstOrNull { it >= today }
            return nextThisYear ?: calculatePolishShoppingSundays(today.year + 1).firstOrNull()
        }

        fun formatCountdown(today: LocalDate, shoppingSunday: LocalDate): String =
            when (val daysUntil = today.daysUntil(shoppingSunday)) {
                0 -> "TODAY!"
                1 -> "tomorrow"
                in 2..<14 -> "in $daysUntil days"
                // round to the nearest whole week instead of truncating (which understated by up to 6 days)
                else -> "in ${(daysUntil + 3) / 7} weeks"
            }

        fun formatShortDate(date: LocalDate): String =
            "${date.day} ${shortMonthNames[date.month.ordinal]}"
    }

    override suspend fun onNewDataSnapshot(snapshot: Snapshot, oldStatus: LocalDate?): UpdateStatus {
        val today = snapshot.timestamp.toLocalDateInCurrentTimeZone()

        return if (today == oldStatus) {
            UpdateStatus.NoNewData
        } else {
            UpdateStatus.NewData(today)
        }
    }

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: LocalDate?,
        instant: Unit?
    ): Unit = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            launch {
                fd.setStaticText(2, "Shopping Sunday")
            }
        }

        if (status == null) {
            fd.setStaticText(20, "no date".center(20))
            return@coroutineScope
        }

        if (redrawStatus) {
            launch {
                val next = findNextShoppingSunday(status)
                fd.setStaticText(
                    20, (if (next == null) {
                        "not available"
                    } else {
                        "${formatShortDate(next)}, ${formatCountdown(status, next)}"
                    }).center(20)
                )
            }
        }
    }
}