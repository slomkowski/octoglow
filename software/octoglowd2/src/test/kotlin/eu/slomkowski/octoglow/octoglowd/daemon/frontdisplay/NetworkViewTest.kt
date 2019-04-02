package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NetworkViewTest {
    @Test
    fun testGetInterfaces() {
        NetworkView.getInterfaces()
    }

    @Test
    fun testGetRouteEntries() {
        val entries = NetworkView.getRouteEntries()
        assertNotNull(entries)
        assertTrue(entries.isNotEmpty())
    }
}