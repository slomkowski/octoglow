package eu.slomkowski.octoglow.octoglowd.daemon.view

import com.uchuhimo.konf.Config
import de.jollyday.HolidayManager
import de.jollyday.ManagerParameters
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.GeoPosKey
import eu.slomkowski.octoglow.octoglowd.calculateSunriseAndSunset
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.lang3.StringUtils
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class CalendarView(
        private val config: Config,
        private val hardware: Hardware)
    : FrontDisplayView("Calendar", Duration.ofMinutes(3), Duration.ofMinutes(1)) {

    data class NameDayRow(
            val day: Int,
            val month: Int,
            val names: List<String>) {
        init {
            require(day in (1..31))
            require(month in (1..12))
            require(names.isNotEmpty())
        }
    }

    companion object : KLogging() {
        fun loadNameDays(csvInputStream: InputStream): List<NameDayRow> = InputStreamReader(csvInputStream, StandardCharsets.UTF_8).use { reader ->
            CSVParser(reader, CSVFormat.DEFAULT).records.map { record ->
                NameDayRow(
                        record.get(0).toInt(),
                        record.get(1).toInt(),
                        record.drop(2).map { it.trim() })
            }
        }

        val displayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd", Locale.ENGLISH)
        val sunriseSunsetTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
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
        val holiday = holidayManager.getHolidays(day, day).firstOrNull()
        val names = nameDays.find { it.month == day.monthValue && it.day == day.dayOfMonth }?.names

        val (sunrise, sunset) = calculateSunriseAndSunset(config[GeoPosKey.latitude], config[GeoPosKey.longitude], day)

        return listOfNotNull(
                holiday?.description?.toUpperCase(),
                "sunrise:${sunrise.format(sunriseSunsetTimeFormatter)}",
                "sunset:${sunset.format(sunriseSunsetTimeFormatter)}",
                names?.joinToString(","))
                .joinToString("; ")
                .let { StringUtils.capitalize(it) }
    }

    fun getDayDescription(day: LocalDate): String = getInfoForDay(day) + "; tomorrow: " + getInfoForDay(day.plusDays(1))

    override suspend fun poolStatusData(): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun poolInstantData(): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
        val fd = hardware.frontDisplay
        val now = LocalDate.now()

        if (redrawStatus) {
            launch { fd.setStaticText(0, now.format(displayDateFormatter)) }
            launch { fd.setScrollingText(Slot.SLOT0, 20, 20, StringUtils.abbreviate(getDayDescription(now), Slot.SLOT0.capacity)) }
        }
    }
}