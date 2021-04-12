package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
internal class AirQualityViewTest {

    @Test
    fun testRetrieveAirQualityData() {
        runBlocking { AirQualityView.retrieveAirQualityData(944) }.let {
            assertNotNull(it.stIndexLevel.levelName)
        }
    }
}