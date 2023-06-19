package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
internal class AirQualityViewTest {

    @Test
    fun testRetrieveAirQualityData() {
        // https://api.gios.gov.pl/pjp-api/rest/station/findAll - all stations

        runBlocking { AirQualityView.retrieveAirQualityData(952) }.let {
            assertNotNull(it.stIndexLevel.levelName)
        }
    }
}