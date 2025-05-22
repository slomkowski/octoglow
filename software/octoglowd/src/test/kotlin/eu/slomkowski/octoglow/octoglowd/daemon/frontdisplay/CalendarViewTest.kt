package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.CalendarView.Companion.formatDate
import eu.slomkowski.octoglow.octoglowd.defaultTestConfig
import io.mockk.mockk
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue


internal class CalendarViewTest {

    companion object : KLogging()

    @Test
    fun testGetDayDescriptionText() {
        val cv = CalendarView(defaultTestConfig, mockk())

        fun assertOk(text: String, year: Int, month: Int, day: Int) {
            val d = LocalDate(year, month, day)
            val realText = cv.getInfoForDay(d)
            logger.debug { "Text for $d is $realText, length: ${realText.length}." }
            assertEquals(text, realText)
        }

        assertOk("Anieli,Kwiryna,Leonarda", 2019, 3, 30)
        assertOk("NEW YEAR; Mieszka,Mieczysława,Marii", 2018, 1, 1)
        assertOk("CHRISTMAS; Anastazji,Eugenii", 2019, 12, 25)
        assertOk("Beniamina,Dobromierza,Leonarda", 2019, 3, 31)
    }

    @Test
    fun testFormatDate() {
        assertEquals("Sunday 1 Dec", formatDate(LocalDate(2019, 12, 1)))
        assertEquals("Monday 18 Mar", formatDate(LocalDate(2019, 3, 18)))
        assertEquals("Thursday 21 Feb", formatDate(LocalDate(2019, 2, 21)))
        assertEquals("Wed, 20 Feb", formatDate(LocalDate(2019, 2, 20)))

        for (noOfDays in (0..200)) {
            val str = formatDate(LocalDate(2019, 2, 1).plus(DatePeriod(0, 0, noOfDays)))
            assertTrue("$str has length ${str.length}") { str.length <= 15 }
        }
    }
}