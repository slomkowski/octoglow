package eu.slomkowski.octoglow.octoglowd

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HistoricalValueTypeTest {

    @Test
    fun testToSnakeCase() {
        assertEquals("MSL_PRESSURE", MSLPressure.databaseSymbol)
        assertEquals("OUTDOOR_TEMPERATURE", OutdoorTemperature.databaseSymbol)
        assertEquals("OUTDOOR_WEAK_BATTERY", OutdoorWeakBattery.databaseSymbol)
        assertEquals("CRYPTOCURRENCY_BTC", Cryptocurrency("BTC").databaseSymbol)
    }
}