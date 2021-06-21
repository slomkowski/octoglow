package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.RemoteSensorsKey
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.HardwareParameterResolver
import eu.slomkowski.octoglow.octoglowd.now
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExtendWith(HardwareParameterResolver::class)
class WeatherSensorViewTest {
    companion object : KLogging()

    @Test
    fun testRedrawDisplay(hardware: Hardware) {

        val config = Config { addSpec(RemoteSensorsKey) }.from.map.kv(
            mapOf(
                "remote-sensors" to mapOf(
                    "indoorChannelId" to 1,
                    "outdoorChannelId" to 2
                )
            )
        )

        val v = WeatherSensorView(config, mockk(), hardware)

        runBlocking {
            hardware.frontDisplay.clear()

            v.redrawDisplay(true, true, now())
            delay(1_000)

            val rr = WeatherSensorView.RemoteReport(
                23.0,
                List(WeatherSensorView.HISTORIC_VALUES_LENGTH) { 10 + 0.3 * it },
                66.0,
                List(WeatherSensorView.HISTORIC_VALUES_LENGTH) { 55.0 + it },
                false
            )

            v.currentReport = WeatherSensorView.CurrentReport(
                now() to rr,
                null
            )

            v.redrawDisplay(false, true, now())

            delay(3_000)

            v.currentReport = WeatherSensorView.CurrentReport(
                now() to rr,
                now() to rr
            )
            v.redrawDisplay(false, true, now())
        }
    }
}