package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import mu.KLogging
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals

internal class NetworkViewTest {
    companion object : KLogging()

    @Test
    fun testGetActiveInterface() {
        val activeInterface = NetworkView.getActiveInterfaceInfo()
        assertNotNull(activeInterface)
    }

    @Test
    fun testGetRouteEntries() {
        val entries = NetworkView.getRouteEntries()
        assertNotNull(entries)
        assertTrue(entries.isNotEmpty())
        logger.info { "Interfaces: $entries." }
    }

    @Test
    fun testParseProcNetWirelessFile() {
        NetworkViewTest::class.java.getResourceAsStream("/proc-net-wireless/1.txt").use { inputStream ->
            NetworkView.parseProcNetWirelessFile(BufferedReader(InputStreamReader(inputStream, StandardCharsets.US_ASCII))).apply {
                assertEquals(1, size)
                get(0).apply {
                    assertEquals("wlp3s0", ifName)
                    assertEquals(48.0, linkQuality)
                    assertEquals(-62.0, signalStrength)
                }
            }
        }

        //todo more tests on different files
    }
}