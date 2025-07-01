package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test


internal class AirQualityViewTest {

    @Test
    fun testRetrieveAirQualityData() {
        // https://api.gios.gov.pl/pjp-api/rest/station/findAll - all stations

        //todo testy
        runBlocking { AirQualityView.retrieveAirQualityData(52) }.let {
            assertNotNull(it.aqIndex.level)
        }
    }
}