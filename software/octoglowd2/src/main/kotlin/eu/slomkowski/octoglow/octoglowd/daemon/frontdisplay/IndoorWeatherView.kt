package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime

class IndoorWeatherView(
        private val config: Config,
        private val databaseLayer: DatabaseLayer,
        hardware: Hardware)
    : FrontDisplayView(hardware,
        "Indoor weather",
        Duration.ofMinutes(4),
        Duration.ofMinutes(1),
        Duration.ofSeconds(31)) {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14
    }

    data class CurrentReport(
            val timestamp: LocalDateTime,
            val lastTemperature: Double?,
            val lastHumidity: Double?,
            val lastPressure: Double?,
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

        if (redrawStatic) {
            launch { fd.setStaticText(0, "Indoor:") }
        }

        if (redrawStatus) {
            launch { fd.setStaticText(8, formatPressure(rep?.lastPressure)) }
            launch { fd.setStaticText(20, formatTemperature(rep?.lastTemperature)) }
            launch { fd.setStaticText(32, formatHumidity(rep?.lastHumidity)) }

            rep?.lastPressure?.let { launch { fd.setOneLineDiffChart(5 * 17, it, rep.historicalPressure, 1.0) } }
            rep?.lastHumidity?.let { launch { fd.setOneLineDiffChart(5 * 37, it, rep.historicalHumidity, 1.0) } }
            rep?.lastPressure?.let { launch { fd.setOneLineDiffChart(5 * 28, it, rep.historicalTemperature, 1.0) } }
        }

        drawProgressBar(rep?.timestamp)

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

        currentReport = CurrentReport(ts,
                rep.temperature,
                rep.humidity,
                mslPressure,
                historicalTemperature.await(),
                historicalPressure.await(),
                historicalHumidity.await())

        UpdateStatus.FULL_SUCCESS
    }
}