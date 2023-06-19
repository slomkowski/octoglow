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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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
        const val HISTORIC_VALUES_LENGTH = 11
        const val TEMPERATURE_CHART_UNIT = 1.0
        const val HUMIDITY_CHART_UNIT = 5.0

        private val MINIMAL_DURATION_BETWEEN_MEASUREMENTS: Duration = 2.minutes

        private val MAXIMAL_DURATION_BETWEEN_MEASUREMENTS: Duration = 5.minutes
    }

    init {
        require(MINIMAL_DURATION_BETWEEN_MEASUREMENTS < MAXIMAL_DURATION_BETWEEN_MEASUREMENTS)
        require(config[RemoteSensorsKey.indoorChannelId] != config[RemoteSensorsKey.outdoorChannelId]) { "indoor and outdoor sensors cannot have identical IDs" }
    }

    data class RemoteReport(
        val lastTemperature: Double,
        val historicalTemperature: List<Double?>,
        val lastHumidity: Double,
        val historicalHumidity: List<Double?>,
        val isWeakBattery: Boolean
    ) {
        init {
            require(lastHumidity in (1.0..100.0)) { "invalid humidity: $lastHumidity" }
            require(lastTemperature in (-40.0..45.0)) { "invalid temperature value: $lastTemperature" }
            require(historicalTemperature.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class CurrentReport(
        val indoorSensor: Pair<Instant, RemoteReport>?,
        val outdoorSensor: Pair<Instant, RemoteReport>?
    )

    var currentReport: CurrentReport? = null

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val rep = currentReport
            val fd = hardware.frontDisplay

            if (redrawStatic) {
                launch {
                    fd.setStaticText(9, "in:")
//                    fd.setStaticText(10, "out:")
                }
            }

            if (redrawStatus) {
                val outs = rep?.outdoorSensor?.second
                val ins = rep?.indoorSensor?.second

                fd.setStaticText(1, formatHumidity(outs?.lastHumidity))
                fd.setStaticText(13, formatHumidity(ins?.lastHumidity))

                fd.setStaticText(20, formatTemperature(outs?.lastTemperature))
                fd.setStaticText(30, formatTemperature(ins?.lastTemperature))

                outs?.let {
                    fd.setOneLineDiffChart(
                        5 * 5,
                        it.lastHumidity,
                        it.historicalHumidity,
                        HUMIDITY_CHART_UNIT
                    )
                }

                ins?.let {
                    fd.setOneLineDiffChart(
                        85,
                        it.lastHumidity,
                        it.historicalHumidity,
                        HUMIDITY_CHART_UNIT
                    )
                }

                outs?.let {
                    fd.setOneLineDiffChart(
                        5 * 20 + 36,
                        it.lastTemperature,
                        it.historicalTemperature,
                        TEMPERATURE_CHART_UNIT
                    )
                }

                ins?.let {
                    fd.setOneLineDiffChart(
                        5 * 30 + 37,
                        it.lastTemperature,
                        it.historicalTemperature,
                        TEMPERATURE_CHART_UNIT
                    )
                }

                if (outs?.isWeakBattery == true) {
                    fd.setStaticText(4, "!")
                }

                if (ins?.isWeakBattery == true) {
                    fd.setStaticText(16, "!")
                }
            }

            drawProgressBar(
                minOf(
                    rep?.indoorSensor?.first ?: Instant.DISTANT_PAST,
                    rep?.outdoorSensor?.first ?: Instant.DISTANT_PAST
                ), now, MAXIMAL_DURATION_BETWEEN_MEASUREMENTS
            )

            Unit
        }

    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.NO_NEW_DATA

    private suspend fun poolRemoteSensor(
        now: Instant,
        receivedReport: RemoteSensorReport?,
        channelIdConfigKey: RequiredItem<Int>,
        previousReport: Pair<Instant, RemoteReport>?,
        temperatureDbKey: HistoricalValueType,
        humidityDbKey: HistoricalValueType,
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
                databaseLayer.insertHistoricalValueAsync(now, humidityDbKey, receivedReport.humidity),
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

            val historicalHumidity =
                databaseLayer.getLastHistoricalValuesByHourAsync(now, humidityDbKey, HISTORIC_VALUES_LENGTH)

            UpdateStatus.FULL_SUCCESS to RemoteReport(
                receivedReport.temperature,
                historicalTemperature.await(),
                receivedReport.humidity,
                historicalHumidity.await(),
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

        val newIndoorReport = poolRemoteSensor(
            now,
            remoteSensorReport,
            RemoteSensorsKey.indoorChannelId,
            oldReport?.indoorSensor,
            IndoorTemperature,
            IndoorHumidity,
            IndoorWeakBattery
        )

        val newOutdoorReport = poolRemoteSensor(
            now,
            remoteSensorReport,
            RemoteSensorsKey.outdoorChannelId,
            oldReport?.outdoorSensor,
            OutdoorTemperature,
            OutdoorHumidity,
            OutdoorWeakBattery
        )

        val statuses = listOf(newOutdoorReport, newIndoorReport)
            .map { it.first }
            .groupingBy { it }
            .eachCount()

        val newReport = CurrentReport(
            takeIfReportAppropriate(newIndoorReport, oldReport?.indoorSensor),
            takeIfReportAppropriate(newOutdoorReport, oldReport?.outdoorSensor)
        )

        if (newReport != oldReport) {
            currentReport = newReport
        }

        return@coroutineScope when {
            statuses[UpdateStatus.FAILURE] == 2 -> {
                UpdateStatus.FAILURE
            }
            statuses[UpdateStatus.NO_NEW_DATA] == 2 -> {
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