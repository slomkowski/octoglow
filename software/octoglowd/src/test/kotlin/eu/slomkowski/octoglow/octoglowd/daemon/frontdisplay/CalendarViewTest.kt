package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.GeoPosKey
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.CalendarView.Companion.formatDate
import io.mockk.mockk
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.*
import kotlin.test.assertTrue

internal class CalendarViewTest {

    companion object : KLogging()

    @Test
    fun testGetDayDescriptionText() {
        val config = Config {
            addSpec(ConfKey)
            addSpec(GeoPosKey)
            set(ConfKey.locale, Locale("pl", "PL"))
        }

        val cv = CalendarView(config, mockk())

        fun assertOk(text: String, year: Int, month: Int, day: Int) {
            val d = LocalDate.of(year, month, day)
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
        assertEquals("Sunday 1 Dec", formatDate(LocalDate.of(2019, 12, 1)))
        assertEquals("Monday 18 Mar", formatDate(LocalDate.of(2019, 3, 18)))
        assertEquals("Thursday 21 Feb", formatDate(LocalDate.of(2019, 2, 21)))
        assertEquals("Wed, 20 Feb", formatDate(LocalDate.of(2019, 2, 20)))

        for (noOfDays in (0..200L)) {
            val str = formatDate(LocalDate.of(2019, 2, 1).plusDays(noOfDays))
            assertTrue("$str has length ${str.length}") { str.length <= 15 }
        }
    }

    @Test
    fun testFormatSunriseSunset() {
        assertEquals("13:45", CalendarView.sunriseSunsetTimeFormatter.format(LocalTime.of(13, 45, 52)))
        assertEquals("6:21", CalendarView.sunriseSunsetTimeFormatter.format(LocalTime.of(6, 21, 1)))
    }
}