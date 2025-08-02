@file:OptIn(ExperimentalTime::class, ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

class LocalSensorViewTest {

    @Test
    fun testRedrawDisplay(): Unit = runBlocking {
        val mockHardware = HardwareMock()
        val localSensorView = LocalSensorView(
            mockk(),
            mockk(),
            mockHardware,
        )

        localSensorView.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Clock.System.now(),
            null,
            null,
        )

        mockHardware.frontDisplay.assertDisplayContent(
            "                    ",
            "--% ---.-°C ---- hPa",
            "--% ---.-°C ---- ppm",
        )

        val currentReport = LocalSensorView.CurrentReport(
            timestamp = Clock.System.now(),
            cycleLength = 5.minutes,
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
            now = Clock.System.now(),
            currentReport,
            null
        )

        mockHardware.frontDisplay.assertDisplayContent(
            "*                   ",
            "45% +23.5°C 1013 hPa",
            "44% +24.1°C  450 ppm",
        )
    }
}