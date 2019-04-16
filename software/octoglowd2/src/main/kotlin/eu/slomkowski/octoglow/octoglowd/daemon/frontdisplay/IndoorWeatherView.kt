package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.IndoorWeatherReport
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime

class IndoorWeatherView(
        private val config: Config,
        private val databaseLayer: DatabaseLayer,
        private val hardware: Hardware)
    : FrontDisplayView("Indoor weather",
        Duration.ofMinutes(4),
        Duration.ofMinutes(1),
        Duration.ofSeconds(31)) {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14
    }

    data class CurrentReport(
            val timestamp: LocalDateTime,
            val lastMeasurement: IndoorWeatherReport,
            val historicalTemperature: List<Double?>,
            val historicalPressure: List<Double?>,
            val historicalHumidity: List<Double?>) {
        init {
            require(historicalPressure.size == HISTORIC_VALUES_LENGTH)
            require(historicalHumidity.size == HISTORIC_VALUES_LENGTH)
            require(historicalTemperature.size == HISTORIC_VALUES_LENGTH)
        }
    }

    private var currentReport: CurrentReport? = null //todo maybe protected by mutex?

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
        val rep = currentReport
        val fd = hardware.frontDisplay

        //todo rewrite the whole view to use pressure

        if (redrawStatic) {
            launch { fd.setStaticText(2, "Weather inside") }
        }

        if (redrawStatus) {
            launch { fd.setStaticText(20, formatTemperature(rep?.lastMeasurement?.temperature)) }
            launch { fd.setStaticText(32, formatHumidity(rep?.lastMeasurement?.humidity)) }

            if (rep != null) {
                launch { fd.setOneLineDiffChart(5 * 37, rep.lastMeasurement.humidity, rep.historicalHumidity, 1.0) }
                launch { fd.setOneLineDiffChart(5 * 28, rep.lastMeasurement.temperature, rep.historicalTemperature, 1.0) }
            }
        }

        Unit
    }

    override suspend fun poolInstantData(): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData() = coroutineScope {
        val rep = hardware.bme280.readReport()

        val ts = LocalDateTime.now()
        val elevation = config[GeoPosKey.elevation]
        val mslPressure = rep.getMeanSeaLevelPressure(elevation)

        logger.info { "Got report : $rep." }
        logger.info { "Mean sea-level pressure at $elevation m is $mslPressure hPa." }
        listOf(
                databaseLayer.insertHistoricalValueAsync(ts, IndoorTemperature, rep.temperature),
                databaseLayer.insertHistoricalValueAsync(ts, IndoorHumidity, rep.humidity),
                databaseLayer.insertHistoricalValueAsync(ts, RealPressure, rep.realPressure),
                databaseLayer.insertHistoricalValueAsync(ts, MSLPressure, mslPressure)
        ).joinAll()

        val historicalTemperature = databaseLayer.getLastHistoricalValuesByHourAsync(ts, IndoorTemperature, HISTORIC_VALUES_LENGTH)
        val historicalHumidity = databaseLayer.getLastHistoricalValuesByHourAsync(ts, IndoorHumidity, HISTORIC_VALUES_LENGTH)
        val historicalPressure = databaseLayer.getLastHistoricalValuesByHourAsync(ts, MSLPressure, HISTORIC_VALUES_LENGTH)

        currentReport = CurrentReport(ts, rep, historicalTemperature.await(), historicalPressure.await(), historicalHumidity.await())

        UpdateStatus.FULL_SUCCESS
    }
}