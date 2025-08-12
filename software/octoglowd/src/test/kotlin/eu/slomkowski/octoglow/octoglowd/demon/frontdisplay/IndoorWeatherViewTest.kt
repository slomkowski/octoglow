@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.IndoorWeatherView.Companion.formatCo2
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import eu.slomkowski.octoglow.octoglowd.testConfig
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class IndoorWeatherViewTest {
    
    @Test
    fun testFormatCo2() {
        assertThat(formatCo2(null)).isEqualTo("---- ppm")
        assertThat(formatCo2(0.0)).isEqualTo("   0 ppm")
        assertThat(formatCo2(420.0)).isEqualTo(" 420 ppm")
        assertThat(formatCo2(1234.0)).isEqualTo("1234 ppm")
        assertThat(formatCo2(9999.0)).isEqualTo(">5000ppm")
        assertThat(formatCo2(10000.0)).isEqualTo(">5000ppm")
    }

    @Test
    fun testRedrawDisplayEmpty() : Unit = runBlocking {
        val hardware = HardwareMock()
        val view = IndoorWeatherView(testConfig, mockk(), hardware)

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Clock.System.now(),
            status = null,
            instant = Unit)

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("ping -- ms gw  -- ms")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("mqtt FAIL! #########")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isEqualTo("IP: ---.---.---.---")
    }
}