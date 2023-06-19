package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import de.jollyday.HolidayManager
import de.jollyday.ManagerParameters
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.GeoPosKey
import eu.slomkowski.octoglow.octoglowd.calculateSunriseAndSunset
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.toLocalDate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import mu.KLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.lang3.StringUtils
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@ExperimentalTime
class CalendarView(
    private val config: Config,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "Calendar",
    3.minutes,
    1.minutes,
    20.seconds
) {

    data class NameDayRow(
        val day: Int,
        val month: Int,
        val names: List<String>
    ) {
        init {
            require(day in (1..31))
            require(month in (1..12))
            require(names.isNotEmpty())
        }
    }

    companion object : KLogging() {
        fun loadNameDays(csvInputStream: InputStream): List<NameDayRow> =
            InputStreamReader(csvInputStream, StandardCharsets.UTF_8).use { reader ->
                CSVParser(reader, CSVFormat.DEFAULT).records.map { record ->
                    NameDayRow(
                        record.get(0).toInt(),
                        record.get(1).toInt(),
                        record.drop(2).map { it.trim() })
                }
            }

        private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d MMM", Locale.ENGLISH)
        private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)
        val sunriseSunsetTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

        fun formatDate(day: LocalDate): String {
            val str = day.toJavaLocalDate().format(fullDateFormatter)
            return when {
                str.length > 15 -> day.toJavaLocalDate().format(shortDateFormatter)
                else -> str
            }
        }
    }

    private val nameDays: Set<NameDayRow>

    private val holidayManager: HolidayManager

    init {
        val locale = config[ConfKey.locale]
        logger.info { "Initializing calendar for $locale." }
        nameDays = requireNotNull(CalendarView::class.java.getResourceAsStream("/name-day/${locale.country}.csv"))
        { "name day CSV for country ${locale.country} doesn't exist" }.use { loadNameDays(it).toSet() }
        holidayManager = HolidayManager.getInstance(ManagerParameters.create(locale))
    }

    fun getInfoForDay(day: LocalDate): String {
        val holiday = holidayManager.getHolidays(day.toJavaLocalDate(), day.toJavaLocalDate()).firstOrNull()
        val names = nameDays.find { it.month == day.monthNumber && it.day == day.dayOfMonth }?.names

        return listOfNotNull(
            holiday?.description?.uppercase(),
            names?.joinToString(",")
        )
            .joinToString("; ")
            .let { StringUtils.capitalize(it) }
    }

    override suspend fun poolStatusData(now: Instant): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val fd = hardware.frontDisplay
            val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).toLocalDate()

            if (redrawStatus) {
                launch {
                    val (sunrise, sunset) = calculateSunriseAndSunset(
                        config[GeoPosKey.latitude],
                        config[GeoPosKey.longitude],
                        today
                    )
                    check(sunrise < LocalTime.of(10, 0))

                    fd.setStaticText(0, formatDate(today))
                    fd.setStaticText(16, sunrise.format(sunriseSunsetTimeFormatter))
                    fd.setStaticText(35, sunset.format(sunriseSunsetTimeFormatter))
                }

                launch { fd.setScrollingText(Slot.SLOT0, 20, 14, getInfoForDay(today).take(Slot.SLOT0.capacity)) }
            }
        }
}