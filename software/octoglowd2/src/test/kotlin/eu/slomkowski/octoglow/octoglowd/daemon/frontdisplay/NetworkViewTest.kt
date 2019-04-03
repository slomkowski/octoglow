package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.NetworkViewKey
import io.mockk.mockk
import mu.KLogging
import org.apache.commons.io.IOUtils
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFails

internal class NetworkViewTest {
    companion object : KLogging()

    @Test
    fun testGetRouteEntries() {
        val entries = NetworkView.getRouteEntries(Paths.get("/bin/ip"))
        assertNotNull(entries)
        assertTrue(entries.isNotEmpty())
        logger.info { "Interfaces: $entries." }
    }

    @Test
    fun testPingAddress() {
        val config = com.uchuhimo.konf.Config {
            addSpec(ConfKey)
            addSpec(NetworkViewKey)
        }

        val active = checkNotNull(NetworkView(config, mockk()).getActiveInterfaceInfo())

        NetworkView.pingAddress(config[NetworkViewKey.pingBinary], active.name, "1.1.1.1", Duration.ofSeconds(5)).apply {
            assertNotNull(this)
        }
    }

    @Test
    fun testParsePingOutput() {
        fun readText(file: String): String = IOUtils.toString(NetworkViewTest::class.java.getResourceAsStream("/ping-output/$file"), StandardCharsets.UTF_8)

        NetworkView.parsePingOutput(readText("1.txt")).apply {
            assertEquals(3, packetsReceived)
            assertEquals(3, packetsTransmitted)
            assertEquals(Duration.ofNanos(8_967_000), rttMin)
            assertEquals(Duration.ofNanos(10_496_000), rttAvg)
            assertEquals(Duration.ofNanos(12_114_000), rttMax)
        }

        assertFails("info about RTT not found in ping output") {
            NetworkView.parsePingOutput(readText("2.txt"))
        }
    }

    @Test
    fun testParseProcNetWirelessFile() {
        fun getList(t: String): List<NetworkView.WifiSignalInfo> = NetworkViewTest::class.java.getResourceAsStream("/proc-net-wireless/$t").use { inputStream ->
            NetworkView.parseProcNetWirelessFile(BufferedReader(InputStreamReader(inputStream, StandardCharsets.US_ASCII)))
        }

        getList("1.txt").apply {
            assertEquals(1, size)
            get(0).apply {
                assertEquals("wlp3s0", ifName)
                assertEquals(48.0, linkQuality)
                assertEquals(-62.0, signalStrength)
            }
        }

        getList("2.txt").apply {
            assertTrue(isEmpty())
        }

        getList("3.txt").apply {
            assertEquals(1, size)
            get(0).apply {
                assertEquals("wlan0", ifName)
                assertEquals(59.0, linkQuality)
                assertEquals(-51.0, signalStrength)
            }
        }

        //todo more tests on different files
    }
}