package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.dataharvesters.AirQualityDataHarvester.Companion.retrieveAirQualityData
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AirQualityDataHarvesterTest {

    @Test
    fun testRetrieveAirQualityData() {
        // https://api.gios.gov.pl/pjp-api/rest/station/findAll - all stations

        //todo testy
        runBlocking { retrieveAirQualityData(52) }.let {
            assertNotNull(it.aqIndex.level)
        }
    }
}