package eu.slomkowski.octoglow.octoglowd.daemon.view

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.OutdoorWeatherReport
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
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
            val historicalTemperature: List<Double?>,
            val historicalHumidity: List<Double?>) {
        init {
            require(historicalHumidity.size == HISTORIC_VALUES_LENGTH)
            require(historicalTemperature.size == HISTORIC_VALUES_LENGTH)
        }
    }

    private var currentReport: CurrentReport? = null //todo maybe protected by mutex?

    override fun getPreferredPoolingInterval(): Duration = Duration.ofSeconds(15)

    override suspend fun redrawDisplay(firstTime: Boolean) = coroutineScope {
        val rep = currentReport
        val fd = hardware.frontDisplay

        if (firstTime) {
            launch { fd.setStaticText(2, "Weather outside") }
        }

        launch { fd.setStaticText(20, formatTemperature(rep?.lastMeasurement?.temperature)) }
        launch { fd.setStaticText(32, formatHumidity(rep?.lastMeasurement?.humidity)) }

        if (rep != null) {
            launch { fd.setOneLineDiffChart(5 * 37, rep.lastMeasurement.humidity, rep.historicalHumidity, 1.0) }
            launch { fd.setOneLineDiffChart(5 * 28, rep.lastMeasurement.temperature, rep.historicalTemperature, 1.0) }

            if (rep.lastMeasurement.batteryIsWeak) {
                launch { fd.setStaticText(19, "!") }
            }
        }
    }

    override suspend fun poolStateUpdate() = coroutineScope {
        val rep = hardware.clockDisplay.getOutdoorWeatherReport()

        if (rep == null) {
            logger.warn { "Invalid report." }
            currentReport = null
            UpdateStatus.FAILURE
        } else {
            when (rep.alreadyReadFlag) {
                false -> UpdateStatus.NO_NEW_DATA
                else -> {
                    val ts = LocalDateTime.now()
                    logger.info { "Got report : $rep." }
                    listOf(
                            databaseLayer.insertHistoricalValue(ts, OutdoorTemperature, rep.temperature),
                            databaseLayer.insertHistoricalValue(ts, OutdoorHumidity, rep.humidity),
                            databaseLayer.insertHistoricalValue(ts, OutdoorWeakBattery, if (rep.batteryIsWeak) {
                                1.0
                            } else {
                                0.0
                            })
                    ).joinAll()

                    val historicalTemperature = databaseLayer.getLastHistoricalValuesByHour(ts, OutdoorTemperature, HISTORIC_VALUES_LENGTH)
                    val historicalHumidity = databaseLayer.getLastHistoricalValuesByHour(ts, OutdoorHumidity, HISTORIC_VALUES_LENGTH)

                    currentReport = CurrentReport(rep, historicalTemperature.await(), historicalHumidity.await())

                    UpdateStatus.FULL_SUCCESS
                }
            }
        }
    }
}