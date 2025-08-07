@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.dataharvesters.NetworkDataHarvester
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.Inet4Address
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime


internal class NetworkViewTest {

    @Test
    fun testFormatPingRtt() {
        assertThat(NetworkView.formatPingRtt(null)).isEqualTo(" -- ms")
        assertThat(NetworkView.formatPingRtt(44_000.nanoseconds)).isEqualTo(" <1 ms")
        assertThat(NetworkView.formatPingRtt(1500.milliseconds)).isEqualTo(">999ms")
        assertThat(NetworkView.formatPingRtt(21.milliseconds)).isEqualTo(" 21 ms")
        assertThat(NetworkView.formatPingRtt(7_800_000.nanoseconds)).isEqualTo("  7 ms")
        assertThat(NetworkView.formatPingRtt(534.milliseconds)).isEqualTo(" 534ms")
        assertThat(NetworkView.formatPingRtt(999.milliseconds)).isEqualTo(" 999ms")

        for (ms in 0..1200) {
            assertThat(NetworkView.formatPingRtt(ms.milliseconds)).hasSize(6)
        }
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

        val s2 = NetworkView.CurrentReport(
            Clock.System.now(),
            2.minutes,
            NetworkDataHarvester.InterfaceInfo(
                "eth0",
                Inet4Address.getByName("192.168.1.2") as Inet4Address,
                Inet4Address.getByName("192.168.1.1") as Inet4Address,
                false,
            ),
            123.milliseconds,
            34.milliseconds,
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