package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LocalSensorViewTest {

    @Test
    fun testRedrawDisplay(): Unit = runBlocking {
        val mockHardware = HardwareMock()
        val localSensorView = LocalSensorView(
            mockk(),
            mockk(),
            mockHardware,
        )

        assertThat(localSensorView.currentReport).isNull()

        localSensorView.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Clock.System.now()
        )

        mockHardware.frontDisplay.assertDisplayContent(
            "                    ",
            "--% ---.-°C ---- hPa",
            "--% ---.-°C ---- ppm",
        )

        localSensorView.currentReport = LocalSensorView.CurrentReport(
            ts = Clock.System.now(),
            bme280temperature = 23.5,
            bme280humidity = 45.0,
            scd40temperature = 24.1,
            scd40humidity = 44.0,
            mslPressure = 1013.25,
            realPressure = 995.0,
            co2concentration = 450.0,
        )

        localSensorView.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Clock.System.now()
        )

        mockHardware.frontDisplay.assertDisplayContent(
            "                    ",
            "45% +23.5°C 1013 hPa",
            "44% +24.1°C  450 ppm",
        )
    }
}