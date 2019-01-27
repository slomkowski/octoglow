package eu.slomkowski.octoglow.octoglowd.view

import eu.slomkowski.octoglow.octoglowd.DatabaseLayer
import eu.slomkowski.octoglow.octoglowd.formatHumidity
import eu.slomkowski.octoglow.octoglowd.formatTemperature
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.OutdoorWeatherReport
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime

class OutdoorWeatherView(
        private val databaseLayer: DatabaseLayer,
        private val hardware: Hardware) : FrontDisplayView {

    override val name: String
        get() = "Outdoor weather"

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14
    }

    data class CurrentReport(
            val lastMeasurement: OutdoorWeatherReport,
            val lastMeasurementTimestamp: LocalDateTime,
            val historicalTemperature: List<Double?>,
            val historicalHumidity: List<Double?>) {
        init {
            require(historicalHumidity.size == HISTORIC_VALUES_LENGTH)
            require(historicalTemperature.size == HISTORIC_VALUES_LENGTH)
        }
    }

    private val fd = hardware.frontDisplay

    private var currentReport: CurrentReport? = null //todo maybe protected by mutex?

    override fun getPreferredPoolingInterval(): Duration = Duration.ofSeconds(30)

    override suspend fun redrawDisplay() = coroutineScope {
        val rep = currentReport

        launch { fd.setStaticText(2, "Weather outside") }

        launch { fd.setStaticText(20, formatTemperature(rep?.lastMeasurement?.temperature)) }
        launch { fd.setStaticText(32, formatHumidity(rep?.lastMeasurement?.humidity)) }

        if (rep != null) {
            launch { fd.setOneLineDiffChart(5 * 37, rep.lastMeasurement.humidity, rep.historicalHumidity, 1.0) }
            launch { fd.setOneLineDiffChart(5 * 28, rep.lastMeasurement.temperature, rep.historicalTemperature, 1.0) }
        }
    }

    override suspend fun poolStateUpdate() = coroutineScope {
        async {
            val rep = hardware.clockDisplay.getOutdoorWeatherReport()

            if (rep == null) {
                logger.warn { "Invalid report." }
                false
            } else {
                when (rep.alreadyReadFlag) {
                    false -> false
                    else -> {
                        val ts = LocalDateTime.now()
                        logger.info { "Got report : $rep." }
                        databaseLayer.insertOutdoorWeatherReport(ts, rep).join()

                        currentReport = databaseLayer.getLastOutdoorWeatherReportsByHour(ts, HISTORIC_VALUES_LENGTH).await().let {
                            check(it.size == HISTORIC_VALUES_LENGTH)
                            CurrentReport(rep, ts, it.map { it?.temperature }, it.map { it?.humidity })
                        }

                        true
                    }
                }
            }
        }
    }
}