package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds


internal class NetworkViewTest {

    @Test
    fun testFormatPingRtt() {
        assertEquals(" -- ms", NetworkView.formatPingRtt(null))
        assertEquals(" <1 ms", NetworkView.formatPingRtt(44_000.nanoseconds))
        assertEquals(">999ms", NetworkView.formatPingRtt(1500.milliseconds))
        assertEquals(" 21 ms", NetworkView.formatPingRtt(21.milliseconds))
        assertEquals("  7 ms", NetworkView.formatPingRtt(7_800_000.nanoseconds))
        assertEquals(" 534ms", NetworkView.formatPingRtt(534.milliseconds))
        assertEquals(" 999ms", NetworkView.formatPingRtt(999.milliseconds))
    }
}