package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.calendar.determineHolidayNamesForDay
import eu.slomkowski.octoglow.octoglowd.calendar.determineNamedaysFor
import eu.slomkowski.octoglow.octoglowd.calendar.holidayNamesSupportCountryCode
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@OptIn(ExperimentalTime::class)
class CalendarView(
    private val config: Config,
    hardware: Hardware
) : FrontDisplayView<LocalDate, Unit>(
    hardware,
    "Calendar",
    null,
    logger,
) {
    override fun preferredDisplayTime(status: LocalDate?) = 20.seconds

    companion object {
        private val logger = KotlinLogging.logger {}

        private val shortDaysOfTheWeek = arrayOf(
            "Mon",
            "Tue",
            "Wed",
            "Thu",
            "Fri",
            "Sat",
            "Sun"
        )

        private val shortMonthNames = arrayOf(
            "Jan",
            "Feb",
            "Mar",
            "Apr",
            "May",
            "Jun",
            "Jul",
            "Aug",
            "Sep",
            "Oct",
            "Nov",
            "Dec"
        )

        private val daysOfTheWeek = arrayOf(
            "Monday",
            "Tuesday",
            "Wednesday",
            "Thursday",
            "Friday",
            "Saturday",
            "Sunday"
        )

        private fun formatDateShort(day: LocalDate): String {
            return "${shortDaysOfTheWeek[day.dayOfWeek.ordinal]}, ${day.day} ${shortMonthNames[day.month.ordinal]}"
        }

        private fun formatDateLong(day: LocalDate): String {
            return "${daysOfTheWeek[day.dayOfWeek.ordinal]} ${day.day} ${shortMonthNames[day.month.ordinal]}"
        }

        fun formatDate(day: LocalDate): String {
            val str = formatDateLong(day)
            return when {
                str.length > 15 -> formatDateShort(day)
                else -> str
            }
        }
    }

    private val countryCode = config.countryCode.uppercase()

    init {
        require(holidayNamesSupportCountryCode(countryCode)) { "unsupported country: $countryCode" }
        logger.info { "Initializing calendar for $countryCode." }
    }

    // Both the holiday text and the sunrise/sunset times depend only on the date, but they are
    // recomputed on every status redraw - and determineHolidayNamesForDay recalculates Easter each
    // time. A single-entry memo is enough: the status handed to redrawDisplay is always today.
    @Volatile
    private var cachedDayInfo: Pair<LocalDate, String>? = null

    @Volatile
    private var cachedSunriseSunset: Pair<LocalDate, Pair<LocalTime, LocalTime>>? = null

    fun getInfoForDay(day: LocalDate): String {
        cachedDayInfo?.let { (cachedDay, cachedInfo) ->
            if (cachedDay == day) {
                return cachedInfo
            }
        }

        val holidayNames = determineHolidayNamesForDay(day, countryCode).map { it.uppercase() }.takeIf { it.isNotEmpty() }
        val names = determineNamedaysFor(day, countryCode).takeIf { it.isNotEmpty() }

        val info = listOfNotNull(
            holidayNames?.joinToString(","),
            names?.joinToString(",")
        )
            .joinToString("; ")
            .let { it.replaceFirstChar { char -> char.uppercase() } }

        cachedDayInfo = day to info
        return info
    }

    private fun sunriseAndSunsetFor(day: LocalDate): Pair<LocalTime, LocalTime> {
        cachedSunriseSunset?.let { (cachedDay, cachedTimes) ->
            if (cachedDay == day) {
                return cachedTimes
            }
        }

        val times = calculateSunriseAndSunset(
            config.geoPosition.latitude,
            config.geoPosition.longitude,
            day,
        )

        cachedSunriseSunset = day to times
        return times
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
        if (status == null) {
            fd.setStaticText(0, "no date")
            return@coroutineScope
        }

        if (redrawStatus) {
            launch {
                val (sunrise, sunset) = sunriseAndSunsetFor(status)
                check(sunrise < LocalTime(10, 0))

                fd.setStaticText(0, formatDate(status))
                fd.setStaticText(15, sunrise.roundToNearestMinute().formatJustHoursMinutes())
                fd.setStaticText(35, sunset.roundToNearestMinute().formatJustHoursMinutes())
            }

            launch { fd.setScrollingText(Slot.SLOT0, 20, 14, getInfoForDay(status).take(Slot.SLOT0.capacity)) }
        }
    }
}