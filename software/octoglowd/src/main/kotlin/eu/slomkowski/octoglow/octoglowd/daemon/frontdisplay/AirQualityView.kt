package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.AirQuality
import eu.slomkowski.octoglow.octoglowd.ConfSingleAirStation
import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.DatabaseLayer
import eu.slomkowski.octoglow.octoglowd.datacollectors.AirQualityDataCollector
import eu.slomkowski.octoglow.octoglowd.datacollectors.AirQualityMeasurement
import eu.slomkowski.octoglow.octoglowd.datacollectors.MeasurementReport
import eu.slomkowski.octoglow.octoglowd.datacollectors.StandardMeasurementReport
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@OptIn(ExperimentalTime::class)
class AirQualityView(
    private val config: Config,
    private val database: DatabaseLayer,
    hardware: Hardware,
) : FrontDisplayView2<AirQualityView.CurrentReport, Unit>(
    hardware,
    "Air quality from powietrze.gios.gov.pl",
    null,
    logger,
) {
    override val preferredDisplayTime: Duration = 13.seconds

    data class SingleStationData(
        val name: String,
        val level: AirQualityDataCollector.AirQualityIndex?,
        val latest: Double?,
        val historical: List<Double?>,
    ) {
        init {
            require(name.isNotBlank())
        }
    }

    data class CurrentReport(
        val timestamp: Instant,
        val cycleLength: Duration,
        val station1: SingleStationData?,
        val station2: SingleStationData?,
    )

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val HISTORIC_VALUES_LENGTH = 14
    }

    private suspend fun getForStation(
        report: StandardMeasurementReport,
        station: ConfSingleAirStation
    ): SingleStationData? {
        val dbKey = AirQuality(station.id)

        val measurement = report.values.firstOrNull { it.type == dbKey } as? AirQualityMeasurement ?: return null

        val history =
            database.getLastHistoricalValuesByHourAsync(report.timestamp, dbKey, HISTORIC_VALUES_LENGTH).await()

        return SingleStationData(
            measurement.name,
            measurement.level.getOrNull(),
            measurement.value.getOrNull(),
            history,
        )
    }

    override suspend fun onNewMeasurementReport(report: MeasurementReport, oldStatus: CurrentReport?): UpdateStatus = coroutineScope {
        if (report !is StandardMeasurementReport) {
            return@coroutineScope UpdateStatus.NoNewData
        }

        val station1 = async { getForStation(report, config.airQuality.station1) }
        val station2 = async { getForStation(report, config.airQuality.station2) }

        if (station1.await() == null && station2.await() == null) {
            return@coroutineScope UpdateStatus.NoNewData
        }

        return@coroutineScope UpdateStatus.NewData(
            CurrentReport(
                report.timestamp,
                report.cycleLength,
                station1.await(),
                station2.await(),
            )
        )
    }

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: CurrentReport?,
        instant: Unit?
    ): Unit = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            launch {
                fd.setStaticText(0, "A")
                fd.setStaticText(20, "Q")
            }
        }

        fun drawForStation(offset: Int, slot: Slot, report: SingleStationData?) {
            launch { fd.setOneLineDiffChart(5 * (offset + 16), report?.latest, report?.historical, 1.0) }

            launch {
                fd.setScrollingText(
                    slot,
                    offset + 2,
                    13,
                    report?.let { "${it.name}: ${it.level?.text ?: "no air quality data"}" } ?: "no station data"
                )
                fd.setStaticText(offset + 19, report?.level?.ordinal?.toString() ?: "-")
            }
        }

        if (redrawStatus) {
            drawForStation(0, Slot.SLOT0, status?.station1)
            drawForStation(20, Slot.SLOT1, status?.station2)
        }

        drawProgressBar(status?.timestamp, now, status?.cycleLength)
    }
}