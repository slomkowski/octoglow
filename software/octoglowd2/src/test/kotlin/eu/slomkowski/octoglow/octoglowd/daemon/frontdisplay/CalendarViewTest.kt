package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.GeoPosKey
import eu.slomkowski.octoglow.octoglowd.poznanCoordinates
import io.mockk.mockk
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

internal class CalendarViewTest {

    companion object : KLogging()

    @Test
    fun testGetDayDescriptionText() {
        val config = Config {
            addSpec(ConfKey)
            addSpec(GeoPosKey)
            set(ConfKey.locale, Locale("pl", "PL"))
            poznanCoordinates.let { (lat, lng) ->
                set(GeoPosKey.latitude, lat)
                set(GeoPosKey.longitude, lng)
            }
        }

        val cv = CalendarView(config, mockk())

        fun assertOk(text: String, year: Int, month: Int, day: Int) {
            val d = LocalDate.of(year, month, day)
            val realText = cv.getInfoForDay(d)
            logger.debug { "Text for $d is $realText, length: ${realText.length}." }
            assertEquals(text, realText)
        }

        assertOk("NEW YEAR; 8:02 - 15:50; Mieszka, Mieczysława, Marii", 2018, 1, 1)
        assertOk("CHRISTMAS; 8:02 - 15:43; Anastazji, Eugenii", 2019, 12, 25)
        assertOk("6:30 - 19:24; Beniamina, Dobromierza, Leonarda", 2019, 3, 31)

        assertEquals("5:01 - 20:38; Serwacego, Roberta, Glorii; tomorrow: 4:59 - 20:39; Bonifacego, Dobiesława, Macieja", cv.getDayDescription(LocalDate.of(2019, 5, 13)))
    }

    @Test
    fun testFormattedDate() {
        assertEquals("Sunday 2019-12-01", CalendarView.displayDateFormatter.format(LocalDate.of(2019, 12, 1)))
        assertEquals("Monday 2019-03-18", CalendarView.displayDateFormatter.format(LocalDate.of(2019, 3, 18)))
        assertEquals("Thursday 2019-02-21", CalendarView.displayDateFormatter.format(LocalDate.of(2019, 2, 21)))

        assertEquals("13:45", CalendarView.sunriseSunsetTimeFormatter.format(LocalTime.of(13, 45, 52)))
        assertEquals("6:21", CalendarView.sunriseSunsetTimeFormatter.format(LocalTime.of(6, 21, 1)))
    }
}