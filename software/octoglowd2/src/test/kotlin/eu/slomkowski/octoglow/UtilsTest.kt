package eu.slomkowski.octoglow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UtilsTest {

    @Test
    fun testFormatHumidity() {
        assertEquals("H:100%", formatHumidity(100.0))
        assertEquals("H: 3%", formatHumidity(3.0))
        assertEquals("H: 0%", formatHumidity(0.0))
        assertEquals("H:24%", formatHumidity(24.1001))
        assertEquals("H:39%", formatHumidity(38.8903))
        assertEquals("H:--%", formatHumidity(null))
    }

    @Test
    fun testFormatTemperature() {
        assertEquals("+24.4\u00B0C", formatTemperature(24.434))
        assertEquals("-12.0\u00B0C", formatTemperature(-12.0))
        assertEquals(" +3.2\u00B0C", formatTemperature(3.21343))
        assertEquals("-12.7\u00B0C", formatTemperature(-12.693423))
        assertEquals(" +0.0\u00B0C", formatTemperature(0.0))
        assertEquals("---.-\u00B0C", formatTemperature(null))
    }
}