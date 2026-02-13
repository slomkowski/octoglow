package eu.slomkowski.octoglow.octoglowd.calendar

import de.jollyday.HolidayManager
import de.jollyday.ManagerParameters
import kotlinx.datetime.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CountryHolidaysKtTest {

    @Test
    fun calculateEasterDate() {
        assertThat(calculateEasterDate(2010)).isEqualTo(LocalDate(2010, Month.APRIL, 4))
        assertThat(calculateEasterDate(2019)).isEqualTo(LocalDate(2019, Month.APRIL, 21))
        assertThat(calculateEasterDate(2020)).isEqualTo(LocalDate(2020, Month.APRIL, 12))
        assertThat(calculateEasterDate(2021)).isEqualTo(LocalDate(2021, Month.APRIL, 4))
        assertThat(calculateEasterDate(2022)).isEqualTo(LocalDate(2022, Month.APRIL, 17))
        assertThat(calculateEasterDate(2023)).isEqualTo(LocalDate(2023, Month.APRIL, 9))
        assertThat(calculateEasterDate(2024)).isEqualTo(LocalDate(2024, Month.MARCH, 31))
        assertThat(calculateEasterDate(2025)).isEqualTo(LocalDate(2025, Month.APRIL, 20))
    }

    @Test
    fun testUsingJollyDay() {
        val locale = "PL"
        val holidayManager = HolidayManager.getInstance(ManagerParameters.create(locale))

        var day = LocalDate(1900, Month.JANUARY, 1)
        do {
            val jollydayHolidays = holidayManager.getHolidays(day.toJavaLocalDate(), day.toJavaLocalDate())
            val myHolidays = determineHolidayNamesForDay(day, locale)

            assertThat(jollydayHolidays).withFailMessage("for date $day").hasSameSizeAs(myHolidays)

            day = day.plus(1, DateTimeUnit.DAY)
        } while (day.year < 2100)
    }
    
    private fun calculatePolishShoppingSundays2(year : Int) = calculatePolishShoppingSundays(year).apply {
        forEach { assertThat(it.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY) }
    }

    @Test
    fun testcalculatePolishShoppingSundays2() {

        // nowelizacja
        assertThat(calculatePolishShoppingSundays2(2025)).containsExactly(
            LocalDate(2025, Month.JANUARY, 26),
            LocalDate(2025, Month.APRIL, 13),
            LocalDate(2025, Month.APRIL, 27),
            LocalDate(2025, Month.JUNE, 29),
            LocalDate(2025, Month.AUGUST, 31),
            LocalDate(2025, Month.DECEMBER, 7),
            LocalDate(2025, Month.DECEMBER, 14),
            LocalDate(2025, Month.DECEMBER, 21),
        )

        assertThat(calculatePolishShoppingSundays2(2026)).containsExactly(
            LocalDate(2026, Month.JANUARY, 25),
            LocalDate(2026, Month.MARCH, 29),
            LocalDate(2026, Month.APRIL, 26),
            LocalDate(2026, Month.JUNE, 28),
            LocalDate(2026, Month.AUGUST, 30),
            LocalDate(2026, Month.DECEMBER, 6),
            LocalDate(2026, Month.DECEMBER, 13),
            LocalDate(2026, Month.DECEMBER, 20),
        )

        assertThat(calculatePolishShoppingSundays2(2027)).containsExactly(
            LocalDate(2027, Month.JANUARY, 31),
            LocalDate(2027, Month.MARCH, 21),
            LocalDate(2027, Month.APRIL, 25),
            LocalDate(2027, Month.JUNE, 27),
            LocalDate(2027, Month.AUGUST, 29),
            LocalDate(2027, Month.DECEMBER, 5),
            LocalDate(2027, Month.DECEMBER, 12),
            LocalDate(2027, Month.DECEMBER, 19),
        )
    }
}