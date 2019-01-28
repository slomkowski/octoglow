package eu.slomkowski.octoglow.octoglowd.view

import eu.slomkowski.octoglow.octoglowd.hardware.OutdoorWeatherReport
import kotlinx.coroutines.Deferred
import java.time.Duration
import java.time.LocalDateTime

class CryptocurrencyView : FrontDisplayView {

    companion object {

    }

    data class CurrentReport(
            val lastMeasurementTimestamp: LocalDateTime,
            val lastMeasurement: OutdoorWeatherReport,
            val historicalTemperature: List<Double?>,
            val historicalHumidity: List<Double?>) {
    }

    override suspend fun redrawDisplay() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun poolStateUpdate(): Deferred<Boolean> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getPreferredPoolingInterval(): Duration = Duration.ofSeconds(30) // change to 5 min

    override val name: String
        get() = "Cryptocurrencies"
}