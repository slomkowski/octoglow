package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import mu.KLogging
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

class WeatherSensorView(
        private val config: Config,
        private val databaseLayer: DatabaseLayer,
        hardware: Hardware)
    : FrontDisplayView(hardware,
        "Weather sensor view",
        Duration.ofSeconds(20),
        Duration.ofSeconds(7),
        Duration.ofSeconds(12)) {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14

        private val MAXIMAL_DURATION_BETWEEN_MEASUREMENTS: Duration = Duration.ofMinutes(5)

        /**
         * BME280 has quite big self-heating. This is the empirically determined temperature offset.
         */
        private const val INDOOR_SENSOR_TEMPERATURE_OFFSET = -0.76
    }

    data class IndoorReport(
            val lastTemperature: Double,
            val lastPressure: Double,
            val historicalTemperature: List<Double?>,
            val historicalPressure: List<Double?>) {
        init {
            require(lastTemperature in (5.0..45.0)) { "invalid temperature value: $lastTemperature" }
            require(lastPressure in (900.0..1100.0)) { "invalid pressure value: $lastPressure" }
            require(historicalPressure.size == HISTORIC_VALUES_LENGTH)
            require(historicalTemperature.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class OutdoorReport(
            val lastTemperature: Double,
            val historicalTemperature: List<Double?>,
            val isWeakBattery: Boolean) {
        init {
            require(lastTemperature in (-40.0..45.0)) { "invalid temperature value: $lastTemperature" }
            require(historicalTemperature.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class CurrentReport(
            val timestamp: ZonedDateTime,
            val indoor: IndoorReport?,
            val outdoor: OutdoorReport?)

    private var currentReport: CurrentReport? = null

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: ZonedDateTime) = coroutineScope {
        val rep = currentReport
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            launch { fd.setStaticText(9, "in:") }
        }

        if (redrawStatus) {
            launch {
                fd.setStaticText(0, formatTemperature(rep?.outdoor?.lastTemperature) + when (rep?.outdoor?.isWeakBattery) {
                    true -> "!"
                    else -> ""
                })
            }
            launch { fd.setStaticText(13, formatTemperature(rep?.indoor?.lastTemperature)) }
            launch { fd.setStaticText(24, formatPressure(rep?.indoor?.lastPressure)) }

            rep?.outdoor?.let { launch { fd.setOneLineDiffChart(5 * 20, it.lastTemperature, it.historicalTemperature, 1.0) } }
            rep?.indoor?.let { launch { fd.setOneLineDiffChart(5 * 33, it.lastPressure, it.historicalPressure, 1.0) } }
            rep?.indoor?.let { launch { fd.setOneLineDiffChart(5 * 37, it.lastTemperature, it.historicalTemperature, 1.0) } }
        }

        drawProgressBar(rep?.timestamp, now, MAXIMAL_DURATION_BETWEEN_MEASUREMENTS)

        Unit
    }

    override suspend fun poolInstantData(now: ZonedDateTime): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData(now: ZonedDateTime) = coroutineScope {
        val oldReport = currentReport

        val newOutdoorSensorData = hardware.clockDisplay.getOutdoorWeatherReport()

        val (outdoorStatus, outdoorReport) = if (newOutdoorSensorData == null) {
            logger.warn { "Invalid outdoor sensor report." }
            UpdateStatus.FAILURE to null
        } else if (!newOutdoorSensorData.alreadyReadFlag || oldReport == null) {
            logger.info { "Got outdoor report : $newOutdoorSensorData." }
            listOf(
                    databaseLayer.insertHistoricalValueAsync(now, OutdoorTemperature, newOutdoorSensorData.temperature),
                    databaseLayer.insertHistoricalValueAsync(now, OutdoorWeakBattery, if (newOutdoorSensorData.batteryIsWeak) {
                        1.0
                    } else {
                        0.0
                    })
            ).joinAll()

            val historicalTemperature = databaseLayer.getLastHistoricalValuesByHourAsync(now, OutdoorTemperature, HISTORIC_VALUES_LENGTH)

            UpdateStatus.FULL_SUCCESS to OutdoorReport(newOutdoorSensorData.temperature, historicalTemperature.await(), newOutdoorSensorData.batteryIsWeak)
        } else { // no new data
            UpdateStatus.NO_NEW_DATA to oldReport.outdoor
        }

        val (indoorStatus, indoorReport) = if (outdoorStatus == UpdateStatus.FULL_SUCCESS
                || Duration.between(oldReport?.timestamp
                        ?: Instant.EPOCH, now) >= MAXIMAL_DURATION_BETWEEN_MEASUREMENTS) {
            val rep = hardware.bme280.readReport()

            val elevation = config[GeoPosKey.elevation]
            val mslPressure = rep.getMeanSeaLevelPressure(elevation)
            val temperature = rep.temperature + INDOOR_SENSOR_TEMPERATURE_OFFSET

            logger.info { "Got BMP280 report : $rep." }
            logger.info { "Mean sea-level pressure at $elevation m is $mslPressure hPa." }

            listOf(
                    databaseLayer.insertHistoricalValueAsync(now, IndoorTemperature, temperature),
                    databaseLayer.insertHistoricalValueAsync(now, RealPressure, rep.realPressure),
                    databaseLayer.insertHistoricalValueAsync(now, MSLPressure, mslPressure)
            ).joinAll()

            val historicalTemperature = databaseLayer.getLastHistoricalValuesByHourAsync(now, IndoorTemperature, HISTORIC_VALUES_LENGTH)
            val historicalPressure = databaseLayer.getLastHistoricalValuesByHourAsync(now, MSLPressure, HISTORIC_VALUES_LENGTH)

            UpdateStatus.FULL_SUCCESS to IndoorReport(temperature, mslPressure, historicalTemperature.await(), historicalPressure.await())
        } else {
            UpdateStatus.NO_NEW_DATA to oldReport?.indoor
        }

        val (overallStatus, newReport) = setOf(indoorStatus, outdoorStatus).let { statuses ->
            val newRep = CurrentReport(now, indoorReport, outdoorReport)
            when {
                statuses == setOf(UpdateStatus.FAILURE, UpdateStatus.FAILURE) -> UpdateStatus.FAILURE to null
                statuses == setOf(UpdateStatus.NO_NEW_DATA, UpdateStatus.FAILURE) -> UpdateStatus.PARTIAL_SUCCESS to oldReport
                statuses.contains(UpdateStatus.FAILURE) -> UpdateStatus.PARTIAL_SUCCESS to newRep
                statuses == setOf(UpdateStatus.NO_NEW_DATA, UpdateStatus.NO_NEW_DATA) -> UpdateStatus.NO_NEW_DATA to oldReport
                else -> UpdateStatus.FULL_SUCCESS to newRep
            }
        }

        currentReport = newReport

        overallStatus
    }
}