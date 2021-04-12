package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.RequiredItem
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.RemoteSensorReport
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import mu.KLogging
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.minutes
import kotlin.time.seconds

@ExperimentalTime
class WeatherSensorView(
    private val config: Config,
    private val databaseLayer: DatabaseLayer,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "Weather sensor view",
    5.seconds,
    2.seconds,
    12.seconds
) {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14

        private val MINIMAL_DURATION_BETWEEN_MEASUREMENTS: Duration = 2.minutes

        private val MAXIMAL_DURATION_BETWEEN_MEASUREMENTS: Duration = 5.minutes
    }

    init {
        require(MINIMAL_DURATION_BETWEEN_MEASUREMENTS < MAXIMAL_DURATION_BETWEEN_MEASUREMENTS)
        require(config[RemoteSensorsKey.indoorChannelId] != config[RemoteSensorsKey.outdoorChannelId]) { "indoor and outdoor sensors cannot have identical IDs" }
    }

    data class LocalReport(
        val lastPressure: Double,
        val historicalPressure: List<Double?>
    ) {
        init {
            require(lastPressure in (900.0..1100.0)) { "invalid pressure value: $lastPressure" }
            require(historicalPressure.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class RemoteReport(
        val lastTemperature: Double,
        val historicalTemperature: List<Double?>,
        val isWeakBattery: Boolean
    ) {
        init {
            require(lastTemperature in (-40.0..45.0)) { "invalid temperature value: $lastTemperature" }
            require(historicalTemperature.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class CurrentReport(
        val localSensor: Pair<Instant, LocalReport>?,
        val indoorSensor: Pair<Instant, RemoteReport>?,
        val outdoorSensor: Pair<Instant, RemoteReport>?
    )

    private var currentReport: CurrentReport? = null

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val rep = currentReport
            val fd = hardware.frontDisplay

            if (redrawStatic) {
                launch { fd.setStaticText(9, "in:") }
            }

            if (redrawStatus) {
                fun drawTemperature(
                    textPosition: Int,
                    graphPosition: Int,
                    rep: RemoteReport?
                ) = launch {
                    fd.setStaticText(
                        textPosition,
                        formatTemperature(rep?.lastTemperature)
                                + when (rep?.isWeakBattery) {
                            true -> "!"
                            else -> ""
                        }
                    )

                    rep?.let {
                        fd.setOneLineDiffChart(
                            graphPosition,
                            it.lastTemperature,
                            it.historicalTemperature,
                            1.0
                        )
                    }
                }

                drawTemperature(0, 5 * 20, rep?.outdoorSensor?.second)
                drawTemperature(13, 5 * 37, rep?.indoorSensor?.second)

                launch { fd.setStaticText(24, formatPressure(rep?.localSensor?.second?.lastPressure)) }

                rep?.localSensor?.second?.let {
                    launch {
                        fd.setOneLineDiffChart(
                            5 * 33,
                            it.lastPressure,
                            it.historicalPressure,
                            1.0
                        )
                    }
                }
            }

            drawProgressBar(
                minOf(
                    rep?.indoorSensor?.first ?: Instant.DISTANT_PAST,
                    rep?.outdoorSensor?.first ?: Instant.DISTANT_PAST,
                    rep?.localSensor?.first ?: Instant.DISTANT_PAST
                ), now, MAXIMAL_DURATION_BETWEEN_MEASUREMENTS
            )

            Unit
        }

    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.NO_NEW_DATA

    private suspend fun poolLocalSensor(
        now: Instant,
        previousReport: Pair<Instant, LocalReport>?
    ): Pair<UpdateStatus, LocalReport?> = coroutineScope {

        if (now - (previousReport?.first ?: Instant.DISTANT_PAST) < MINIMAL_DURATION_BETWEEN_MEASUREMENTS) {
//            logger.debug("Values for local sensor saved at {}, skipping.", previousReport?.first)
            return@coroutineScope UpdateStatus.NO_NEW_DATA to null
        }

        try {
            val rep = hardware.bme280.readReport()

            val elevation = config[GeoPosKey.elevation]
            val mslPressure = rep.getMeanSeaLevelPressure(elevation)

            logger.debug { "Got BMP280 report : $rep." }
            logger.debug { "Mean sea-level pressure at $elevation m is $mslPressure hPa." }

            listOf(
                databaseLayer.insertHistoricalValueAsync(now, RealPressure, rep.realPressure),
                databaseLayer.insertHistoricalValueAsync(now, MSLPressure, mslPressure)
            ).joinAll()

            val historicalPressure =
                databaseLayer.getLastHistoricalValuesByHourAsync(now, MSLPressure, HISTORIC_VALUES_LENGTH)

            UpdateStatus.FULL_SUCCESS to LocalReport(
                mslPressure,
                historicalPressure.await()
            )
        } catch (e: Exception) {
            logger.error(e) { "Error during updating state of local sensor." }
            UpdateStatus.FAILURE to null
        }
    }

    private suspend fun poolRemoteSensor(
        now: Instant,
        receivedReport: RemoteSensorReport?,
        channelIdConfigKey: RequiredItem<Int>,
        previousReport: Pair<Instant, RemoteReport>?,
        temperatureDbKey: HistoricalValueType,
        weakBatteryDbKey: HistoricalValueType
    ): Pair<UpdateStatus, RemoteReport?> = coroutineScope {

        if (receivedReport == null || receivedReport.alreadyReadFlag) {
            return@coroutineScope UpdateStatus.NO_NEW_DATA to null
        }

        if (config[channelIdConfigKey] != receivedReport.sensorId) {
            return@coroutineScope UpdateStatus.NO_NEW_DATA to null
        }

        // don't update report if it is younger than MINIMAL_DURATION_BETWEEN_MEASUREMENTS
        if (now - (previousReport?.first ?: Instant.DISTANT_PAST) < MINIMAL_DURATION_BETWEEN_MEASUREMENTS) {
            logger.debug(
                "Values for remote sensor (channel {}) saved at {}, skipping.",
                config[channelIdConfigKey],
                previousReport?.first
            )
            return@coroutineScope UpdateStatus.NO_NEW_DATA to null
        }

        try {
            listOf(
                databaseLayer.insertHistoricalValueAsync(now, temperatureDbKey, receivedReport.temperature),
                databaseLayer.insertHistoricalValueAsync(
                    now, weakBatteryDbKey, if (receivedReport.batteryIsWeak) {
                        1.0
                    } else {
                        0.0
                    }
                )
            ).joinAll()

            val historicalTemperature =
                databaseLayer.getLastHistoricalValuesByHourAsync(now, temperatureDbKey, HISTORIC_VALUES_LENGTH)

            UpdateStatus.FULL_SUCCESS to RemoteReport(
                receivedReport.temperature,
                historicalTemperature.await(),
                receivedReport.batteryIsWeak
            )
        } catch (e: Exception) {
            logger.error(e) { "Error during updating state of remote sensor (channel ${receivedReport.sensorId})." }
            UpdateStatus.FAILURE to null
        }
    }

    override suspend fun poolStatusData(now: Instant) = coroutineScope {

        // if status failure, set null; other
        fun <T : Any> takeIfReportAppropriate(
            repPair: Pair<UpdateStatus, T?>,
            oldRep: Pair<Instant, T>?
        ): Pair<Instant, T>? {
            val (newStatus, newRep) = repPair

            return when (newStatus) {
                UpdateStatus.FAILURE -> null
                UpdateStatus.FULL_SUCCESS -> now to checkNotNull(newRep) { "status is $newStatus, but $newRep is null" }
                UpdateStatus.NO_NEW_DATA -> oldRep?.takeIf { (prevTimestamp, _) -> now - prevTimestamp < MAXIMAL_DURATION_BETWEEN_MEASUREMENTS }
                else -> throw IllegalStateException("status $newStatus not supported")
            }
        }

        val oldReport = currentReport

        val remoteSensorReport = hardware.clockDisplay.retrieveRemoteSensorReport()

        val newLocal = poolLocalSensor(now, oldReport?.localSensor)

        val newIndoorReport = poolRemoteSensor(
            now,
            remoteSensorReport,
            RemoteSensorsKey.indoorChannelId,
            oldReport?.indoorSensor,
            IndoorTemperature,
            IndoorWeakBattery
        )

        val newOutdoorReport = poolRemoteSensor(
            now,
            remoteSensorReport,
            RemoteSensorsKey.outdoorChannelId,
            oldReport?.outdoorSensor,
            OutdoorTemperature,
            OutdoorWeakBattery
        )

        val statuses = listOf(newOutdoorReport, newIndoorReport, newLocal)
            .map { it.first }
            .groupingBy { it }
            .eachCount()

        val newReport = CurrentReport(
            takeIfReportAppropriate(newLocal, oldReport?.localSensor),
            takeIfReportAppropriate(newIndoorReport, oldReport?.indoorSensor),
            takeIfReportAppropriate(newOutdoorReport, oldReport?.outdoorSensor)
        )

        if (newReport != oldReport) {
            currentReport = newReport
        }

        return@coroutineScope when {
            statuses[UpdateStatus.FAILURE] == 3 -> {
                UpdateStatus.FAILURE
            }
            statuses[UpdateStatus.NO_NEW_DATA] == 3 -> {
                UpdateStatus.NO_NEW_DATA
            }
            statuses[UpdateStatus.FAILURE] ?: 0 > 0 -> {
                UpdateStatus.PARTIAL_SUCCESS
            }
            else -> {
                UpdateStatus.FULL_SUCCESS
            }
        }
    }
}