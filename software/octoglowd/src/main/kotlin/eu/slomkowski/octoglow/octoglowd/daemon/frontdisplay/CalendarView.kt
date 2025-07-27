package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.calendar.determineHolidayNamesForDay
import eu.slomkowski.octoglow.octoglowd.calendar.determineNamedaysFor
import eu.slomkowski.octoglow.octoglowd.calendar.holidayNamesSupportCountryCode
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class CalendarView(
    private val config: Config,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "Calendar",
    3.minutes,
    1.minutes,
) {
    override val preferredDisplayTime = 20.seconds

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
            return "${shortDaysOfTheWeek[day.dayOfWeek.ordinal]}, ${day.dayOfMonth} ${shortMonthNames[day.month.value - 1]}"
        }

        private fun formatDateLong(day: LocalDate): String {
            return "${daysOfTheWeek[day.dayOfWeek.ordinal]} ${day.dayOfMonth} ${shortMonthNames[day.month.value - 1]}"
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

    fun getInfoForDay(day: LocalDate): String {
        val holidayNames = determineHolidayNamesForDay(day, countryCode).map { it.uppercase() }.takeIf { it.isNotEmpty() }
        val names = determineNamedaysFor(day, countryCode).takeIf { it.isNotEmpty() }

        return listOfNotNull(
            holidayNames?.joinToString(","),
            names?.joinToString(",")
        )
            .joinToString("; ")
            .let { it.replaceFirstChar { char -> char.uppercase() } }
    }

    override suspend fun pollStatusData(now: Instant): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun pollInstantData(now: Instant): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val fd = hardware.frontDisplay
            val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).toLocalDate()

            if (redrawStatus) {
                launch {
                    val (sunrise, sunset) = calculateSunriseAndSunset(
                        config.geoPosition.latitude,
                        config.geoPosition.longitude,
                        today
                    )
                    check(sunrise < LocalTime(10, 0))

                    fd.setStaticText(0, formatDate(today))
                    fd.setStaticText(15, sunrise.roundToNearestMinute().formatJustHoursMinutes())
                    fd.setStaticText(35, sunset.roundToNearestMinute().formatJustHoursMinutes())
                }

                launch { fd.setScrollingText(Slot.SLOT0, 20, 14, getInfoForDay(today).take(Slot.SLOT0.capacity)) }
            }
        }
}