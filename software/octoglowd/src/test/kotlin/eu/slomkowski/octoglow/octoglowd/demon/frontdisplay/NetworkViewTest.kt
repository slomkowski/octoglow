@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.TimestampedObject
import eu.slomkowski.octoglow.octoglowd.dataharvesters.NetworkDataHarvester
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.Inet4Address
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime


internal class NetworkViewTest {

    @Test
    fun testFormatPingRtt() {
        val now = Clock.System.now()
        assertThat(NetworkView.formatPingRtt(now, null)).isEqualTo(" -- ms")
        assertThat(NetworkView.formatPingRtt(now, TimestampedObject(now, 44_000.nanoseconds))).isEqualTo(" <1 ms")
        assertThat(NetworkView.formatPingRtt(now, TimestampedObject(now, 1500.milliseconds))).isEqualTo(">999ms")
        assertThat(NetworkView.formatPingRtt(now, TimestampedObject(now, 21.milliseconds))).isEqualTo(" 21 ms")
        assertThat(NetworkView.formatPingRtt(now, TimestampedObject(now, 7_800_000.nanoseconds))).isEqualTo("  7 ms")
        assertThat(NetworkView.formatPingRtt(now, TimestampedObject(now, 534.milliseconds))).isEqualTo(" 534ms")
        assertThat(NetworkView.formatPingRtt(now, TimestampedObject(now, 999.milliseconds))).isEqualTo(" 999ms")
        assertThat(NetworkView.formatPingRtt(now, TimestampedObject(now, 7_800_000.nanoseconds))).isEqualTo("  7 ms")

        for (ms in 0..1200) {
            assertThat(NetworkView.formatPingRtt(now, TimestampedObject(now, ms.milliseconds))).matches { it.length == 6 }
        }

        assertThat(NetworkView.formatPingRtt(now, TimestampedObject(now.minus(1.hours), 33.milliseconds))).isEqualTo(" -- ms")
    }

    @Test
    fun testFormatInterfaceInfo() {
        val now = Clock.System.now()
        assertThat(NetworkView.formatInterfaceInfo(now, null)).isEqualTo("IP: ---.---.---.---")

        val info1 = TimestampedObject(
            now,
            NetworkDataHarvester.InterfaceInfo(
                "eth0",
                Inet4Address.getByName("192.168.1.2") as Inet4Address,
                Inet4Address.getByName("192.168.1.1") as Inet4Address,
                false,
            )
        )
        assertThat(NetworkView.formatInterfaceInfo(now, info1)).isEqualTo("eth IP: 192.168.1.2")

        val info2 = TimestampedObject(
            now,
            NetworkDataHarvester.InterfaceInfo(
                "wlan0",
                Inet4Address.getByName("10.0.0.15") as Inet4Address,
                Inet4Address.getByName("10.0.0.1") as Inet4Address,
                true,
            )
        )
        assertThat(NetworkView.formatInterfaceInfo(now, info2)).isEqualTo("wl IP: 10.0.0.15")

        val oldInfo = TimestampedObject(
            now.minus(1.hours),
            NetworkDataHarvester.InterfaceInfo(
                "eth0",
                Inet4Address.getByName("192.168.1.2") as Inet4Address,
                Inet4Address.getByName("192.168.1.1") as Inet4Address,
                false,
            )
        )
        assertThat(NetworkView.formatInterfaceInfo(now, oldInfo)).isEqualTo("IP: ---.---.---.---")

        val veryOldInfo = TimestampedObject(
            now.minus(20.minutes),
            NetworkDataHarvester.InterfaceInfo(
                "eth0",
                Inet4Address.getByName("192.168.1.2") as Inet4Address,
                Inet4Address.getByName("192.168.1.1") as Inet4Address,
                false,
            )
        )
        assertThat(NetworkView.formatInterfaceInfo(now, veryOldInfo)).isEqualTo("IP: ---.---.---.---")
    }

    @Test
    fun testRedrawDisplay(): Unit = runBlocking {
        val hardware = HardwareMock()
        val nv = NetworkView(hardware)

        val s1 = NetworkView.CurrentReport(
            Clock.System.now(),
            2.minutes,
            null,
            null,
            null,
            false,
        )

        nv.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Clock.System.now(),
            status = s1,
            instant = Unit,
        )
        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("ping -- ms gw  -- ms")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("mqtt FAIL! #########")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isEqualTo("IP: ---.---.---.---")

        val now = Clock.System.now()
        val s2 = NetworkView.CurrentReport(
            now,
            2.minutes,
            TimestampedObject(
                now, NetworkDataHarvester.InterfaceInfo(
                    "eth0",
                    Inet4Address.getByName("192.168.1.2") as Inet4Address,
                    Inet4Address.getByName("192.168.1.1") as Inet4Address,
                    false,
                )
            ),
            TimestampedObject(now, 123.milliseconds),
            TimestampedObject(now, 34.milliseconds),
            true,
        )

        nv.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Clock.System.now(),
            status = s2,
            instant = Unit,
        )
        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("ping 123ms gw  34 ms")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("mqtt OK    #########")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isEqualTo("eth IP: 192.168.1.2")
    }
}