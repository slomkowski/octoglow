package eu.slomkowski.octoglow.octoglowd.daemon.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class GeigerViewTest {

    @Test
    fun testFormat() {
        assertEquals("---V", GeigerView.formatVoltage(null))
        assertEquals("134V", GeigerView.formatVoltage(134.4323))
        assertEquals(" 22V", GeigerView.formatVoltage(21.89323))

        assertEquals("-- CPM", GeigerView.formatCPM(null))
        assertEquals("24 CPM", GeigerView.formatCPM(23.8))
        assertEquals(" 1 CPM", GeigerView.formatCPM(1.3))

        assertEquals("-.-- uSv/h", GeigerView.formatUSVh(null))
        assertEquals("0.12 uSv/h", GeigerView.formatUSVh(0.123))
        assertEquals("0.02 uSv/h", GeigerView.formatUSVh(0.02))
    }

    @Test
    fun testCalculate() {
        assertEquals(0.108, GeigerView.calculateUSVh(81, Duration.ofMinutes(5)), 0.001)

        Duration.ofMinutes(5).let { min5 ->
            assertEquals(0, GeigerView.getSegmentNumber(Duration.ZERO, min5))
            assertEquals(0, GeigerView.getSegmentNumber(Duration.ofSeconds(3), min5))
            assertEquals(0, GeigerView.getSegmentNumber(Duration.ofSeconds(14), min5))
            assertEquals(0, GeigerView.getSegmentNumber(Duration.ofMillis(14_999), min5))

            assertEquals(1, GeigerView.getSegmentNumber(Duration.ofSeconds(15), min5))
            assertEquals(1, GeigerView.getSegmentNumber(Duration.ofSeconds(23), min5))

            assertEquals(0, GeigerView.getSegmentNumber(Duration.ofSeconds(1), min5))
            assertEquals(19, GeigerView.getSegmentNumber(Duration.ofSeconds(299), min5))
            assertEquals(19, GeigerView.getSegmentNumber(min5, min5))
        }
    }
}