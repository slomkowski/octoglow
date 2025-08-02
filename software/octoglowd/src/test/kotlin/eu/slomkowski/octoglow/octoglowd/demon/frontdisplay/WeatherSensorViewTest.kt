@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.ConfRemoteSensors
import eu.slomkowski.octoglow.octoglowd.defaultTestConfig
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.HardwareParameterResolver
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime


@ExtendWith(HardwareParameterResolver::class)
class WeatherSensorViewTest {

    @Test
    fun testRedrawDisplay(hardware: Hardware) {

        val cycleLength = 5.minutes

        val config = defaultTestConfig.copy(remoteSensors = ConfRemoteSensors(indoorChannelId = 1, outdoorChannelId = 2))

        val v = WeatherSensorView(config, mockk(), hardware)

        runBlocking {
            hardware.frontDisplay.clear()

            v.redrawDisplay(redrawStatic = true, redrawStatus = true, now = Clock.System.now(), null, null)
            delay(1_000)

            val rr = WeatherSensorView.RemoteReport(
                23.0,
                List(WeatherSensorView.HISTORIC_VALUES_LENGTH) { 10 + 0.3 * it },
                66.0,
                List(WeatherSensorView.HISTORIC_VALUES_LENGTH) { 55.0 + it },
                false
            )

            val currentReport1 = WeatherSensorView.CurrentReport(
                cycleLength,
                Clock.System.now() to rr,
                null
            )

            v.redrawDisplay(redrawStatic = false, redrawStatus = true, now = Clock.System.now(), currentReport1, null)

            delay(3_000)

            val currentReport2 = WeatherSensorView.CurrentReport(
                cycleLength,
                Clock.System.now() to rr,
                Clock.System.now() to rr,
            )
            v.redrawDisplay(false, true, now = Clock.System.now(), currentReport2, null)
        }
    }
}