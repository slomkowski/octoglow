package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.test.assertFails

internal class AirQualityViewTest {

    @Test
    fun testRetrieveAirQualityDataNoAirQuality() {
        assertFails("no air quality index for station") {
            runBlocking { AirQualityView.retrieveAirQualityData(943) }
        }
    }

    @Test
    fun testRetrieveAirQualityData() {
        runBlocking { AirQualityView.retrieveAirQualityData(11477) }.let {
            assertNotNull(it.stIndexLevel.levelName)
        }
    }
}