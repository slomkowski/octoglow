package eu.slomkowski.octoglow.octoglowd.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Copied from https://github.com/focus-shift/jollyday/blob/main/jollyday-core/src/main/java/de/focus_shift/jollyday/core/parser/functions/CalculateGregorianEasterSunday.java
 */
fun calculateEasterDate(year: Int): LocalDate {
    val a: Int = year % 19
    val b: Int = year / 100
    val c: Int = year % 100
    val d: Int = b / 4
    val e: Int = b % 4
    val f: Int = (b + 8) / 25
    val g: Int = (b - f + 1) / 3
    val h: Int = (19 * a + b - d - g + 15) % 30
    val i: Int = c / 4
    val j: Int = c % 4
    val k: Int = (32 + 2 * e + 2 * i - h - j) % 7
    val l: Int = (a + 11 * h + 22 * k) / 451
    val x: Int = h + k - 7 * l + 114
    val month: Int = x / 31
    val day: Int = x % 31 + 1

    return LocalDate(year, if (month == 3) Month.MARCH else Month.APRIL, day)
}

enum class ChristianHolidayType {
    GOOD_FRIDAY,
    EASTER_MONDAY,
    ASCENSION_DAY,
    WHIT_MONDAY,
    CORPUS_CHRISTI,
    MAUNDY_THURSDAY,
    ASH_WEDNESDAY,
    MARDI_GRAS,
    GENERAL_PRAYER_DAY,
    CLEAN_MONDAY,
    SHROVE_MONDAY,
    PENTECOST,
    CARNIVAL,
    EASTER_SATURDAY,
    EASTER_TUESDAY,
    SACRED_HEART,
    EASTER,
    PENTECOST_MONDAY,
    WHIT_SUNDAY
}

sealed class Holiday {

    data class Fixed(
        val month: Month,
        val day: Int,
        override val name: String
    ) : Holiday() {
        init {
            require(day in (1..31))
        }

        override fun calculateDate(year: Int) = LocalDate(year, month, day)
    }

    data class Christian(
        val type: ChristianHolidayType,
        override val name: String
    ) : Holiday() {
        override fun calculateDate(year: Int): LocalDate {
            val easter = calculateEasterDate(year)
            return when (type) {
                ChristianHolidayType.EASTER -> easter
                ChristianHolidayType.EASTER_MONDAY -> easter.plus(1, DateTimeUnit.DAY)
                ChristianHolidayType.WHIT_SUNDAY -> easter.plus(49, DateTimeUnit.DAY)
                ChristianHolidayType.CORPUS_CHRISTI -> easter.plus(60, DateTimeUnit.DAY)
                ChristianHolidayType.GOOD_FRIDAY -> easter.plus(-2, DateTimeUnit.DAY)
                ChristianHolidayType.ASCENSION_DAY -> easter.plus(39, DateTimeUnit.DAY)
                ChristianHolidayType.ASH_WEDNESDAY -> easter.plus(-46, DateTimeUnit.DAY)
                ChristianHolidayType.CARNIVAL -> easter.plus(-47, DateTimeUnit.DAY)
                ChristianHolidayType.CLEAN_MONDAY, ChristianHolidayType.SHROVE_MONDAY -> easter.plus(-48, DateTimeUnit.DAY)
                ChristianHolidayType.EASTER_SATURDAY -> easter.plus(-1, DateTimeUnit.DAY)
                ChristianHolidayType.EASTER_TUESDAY -> easter.plus(2, DateTimeUnit.DAY)
                ChristianHolidayType.GENERAL_PRAYER_DAY -> easter.plus(26, DateTimeUnit.DAY)
                ChristianHolidayType.MAUNDY_THURSDAY -> easter.plus(-3, DateTimeUnit.DAY)
                ChristianHolidayType.MARDI_GRAS -> easter.plus(-48, DateTimeUnit.DAY)
                ChristianHolidayType.PENTECOST, ChristianHolidayType.PENTECOST_MONDAY, ChristianHolidayType.WHIT_MONDAY -> easter.plus(50, DateTimeUnit.DAY)
                ChristianHolidayType.SACRED_HEART -> easter.plus(68, DateTimeUnit.DAY)
            }
        }
    }

    abstract val name: String

    abstract fun calculateDate(year: Int): LocalDate
}

// as of 2023-11-07
private val holidayMap = mapOf(
    "PL" to listOf(
        Holiday.Fixed(Month.JANUARY, 1, "New Year"),
        Holiday.Fixed(Month.JANUARY, 6, "Epiphany"),
        Holiday.Christian(ChristianHolidayType.EASTER, "Easter"),
        Holiday.Christian(ChristianHolidayType.EASTER_MONDAY, "Easter Monday"),
        Holiday.Fixed(Month.MAY, 1, "Labour Day"),
        Holiday.Fixed(Month.MAY, 3, "Constitution Day"),
        Holiday.Christian(ChristianHolidayType.WHIT_SUNDAY, "Whit Sunday"),
        Holiday.Christian(ChristianHolidayType.CORPUS_CHRISTI, "Corpus Christi"),
        Holiday.Fixed(Month.AUGUST, 15, "Armed Forces Day"),
        Holiday.Fixed(Month.NOVEMBER, 1, "All Saints' Day"),
        Holiday.Fixed(Month.NOVEMBER, 11, "Independence Day"),
        Holiday.Fixed(Month.DECEMBER, 25, "Christmas"),
        Holiday.Fixed(Month.DECEMBER, 26, "Second Day of Christmas"),
    )
)

fun holidayNamesSupportCountryCode(countryCode: String): Boolean = holidayMap.containsKey(countryCode)

/**
 * @return holiday name if given day is a holiday, null otherwise
 */
fun determineHolidayNamesForDay(date: LocalDate, countryCode: String): Set<String> {
    val countryHolidays = requireNotNull(holidayMap[countryCode.uppercase()]) { "unsupported country: $countryCode" }
    return countryHolidays.filter { it.calculateDate(date.year) == date }.map { it.name }.toSet()
}