package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.NetworkViewKey
import eu.slomkowski.octoglow.octoglowd.readToString
import mu.KLogging
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.test.assertFails

internal class NetworkViewTest {
    companion object : KLogging()

    @Test
    fun testPingAddress() {
        val config = com.uchuhimo.konf.Config {
            addSpec(ConfKey)
            addSpec(NetworkViewKey)
        }

        val active = checkNotNull(NetworkView.getActiveInterfaceInfo())

        NetworkView.pingAddressAndGetRtt(config[NetworkViewKey.pingBinary], active.name, "1.1.1.1", Duration.ofSeconds(5), 4)
            .apply {
                assertNotNull(this)
                logger.info { "Ping stats: $this" }
            }

        assertFails {
            NetworkView.pingAddressAndGetRtt(
                config[NetworkViewKey.pingBinary],
                active.name,
                "254.254.254.254",
                Duration.ofSeconds(3),
                2
            )
        }
    }

    @Test
    fun testParsePingOutput() {
        fun readText(file: String): String = NetworkViewTest::class.java.getResourceAsStream("/ping-output/$file").use {
            it.readToString()
        }

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
    fun testCreateIpFromHexString() {

        fun checkIp(str: String, textual: String) {
            val expected = InetAddress.getByName(textual)
            logger.debug { "IP: $expected = str" }
            assertEquals(expected, NetworkView.createIpFromHexString(str))
        }

        assertFails {
            checkIp("000000003", "0.0.0.0")
        }

        assertFails {
            checkIp("000000hh", "0.0.0.0")
        }

        checkIp("00000000", "0.0.0.0")
        checkIp("0009A8C0", "192.168.9.0")
        checkIp("0009000A", "10.0.9.0")
    }

    @Test
    fun testParseProcNetRouteFile() {
        fun getList(t: String): List<NetworkView.RouteEntry> =
            NetworkViewTest::class.java.getResourceAsStream("/proc-net-route/$t").use { inputStream ->
                NetworkView.parseProcNetRouteFile(
                    BufferedReader(
                        InputStreamReader(
                            inputStream,
                            StandardCharsets.US_ASCII
                        )
                    )
                )
            }

        getList("1.txt").apply {
            assertEquals(4, size)

            get(0).apply {
                assertEquals("enp0s31f6", dev)
                assertEquals(InetAddress.getByName("0.0.0.0"), dst)
                assertEquals(InetAddress.getByName("192.168.9.1"), gateway)
                assertEquals(100, metric)
            }

            get(1).apply {
                assertEquals("wlp3s0", dev)
                assertEquals(InetAddress.getByName("0.0.0.0"), dst)
                assertEquals(InetAddress.getByName("192.168.9.1"), gateway)
                assertEquals(600, metric)
            }
        }

        getList("2.txt").apply {
            assertEquals(3, size)

            get(0).apply {
                assertEquals("br0", dev)
                assertEquals(InetAddress.getByName("0.0.0.0"), dst)
                assertEquals(InetAddress.getByName("192.168.9.1"), gateway)
                assertEquals(0, metric)
            }

            get(1).apply {
                assertEquals("tun0", dev)
                assertEquals(InetAddress.getByName("10.0.9.0"), dst)
                assertEquals(InetAddress.getByName("0.0.0.0"), gateway)
                assertEquals(0, metric)
            }
        }
    }


    @Test
    fun testFormatPingRtt() {
        assertEquals(" -- ms", NetworkView.formatPingRtt(null))
        assertEquals(" <1 ms", NetworkView.formatPingRtt(Duration.ofNanos(44_000)))
        assertEquals(">999ms", NetworkView.formatPingRtt(Duration.ofMillis(1500)))
        assertEquals(" 21 ms", NetworkView.formatPingRtt(Duration.ofMillis(21)))
        assertEquals("  7 ms", NetworkView.formatPingRtt(Duration.ofNanos(7_800_000)))
        assertEquals(" 534ms", NetworkView.formatPingRtt(Duration.ofMillis(534)))
        assertEquals(" 999ms", NetworkView.formatPingRtt(Duration.ofMillis(999)))
    }
}