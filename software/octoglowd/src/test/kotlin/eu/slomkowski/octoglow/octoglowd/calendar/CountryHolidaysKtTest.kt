package eu.slomkowski.octoglow.octoglowd.calendar

import de.jollyday.HolidayManager
import de.jollyday.ManagerParameters
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Month

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
}