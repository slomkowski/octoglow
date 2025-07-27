package eu.slomkowski.octoglow.octoglowd

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MeasurementTypeTest {

    @Test
    fun testToSnakeCase() {
        assertEquals("OUTDOOR_TEMPERATURE", OutdoorTemperature.databaseSymbol)
        assertEquals("OUTDOOR_WEAK_BATTERY", OutdoorWeakBattery.databaseSymbol)

        assertEquals("CRYPTOCURRENCY_BTC", Cryptocurrency("BTC").databaseSymbol)

        assertEquals("STOCK_ABC", Stock("ABC").databaseSymbol)
        assertEquals("STOCK__ABC", Stock("^ABC").databaseSymbol)
        assertEquals("STOCK__REKE", Stock("^Reke").databaseSymbol)

        assertEquals("BME280_HUMIDITY", Bme280Humidity.databaseSymbol)

    }
}