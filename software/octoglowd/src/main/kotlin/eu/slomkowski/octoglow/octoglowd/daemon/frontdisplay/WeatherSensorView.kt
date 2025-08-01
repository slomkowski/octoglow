@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.datacollectors.MeasurementReport
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


class WeatherSensorView(
    private val config: Config,
    private val databaseLayer: DatabaseLayer,
    hardware: Hardware
) : FrontDisplayView2<WeatherSensorView.CurrentReport, Unit>(
    hardware,
    "Weather sensor",
    null,
    logger,
) {
    override fun preferredDisplayTime(status: CurrentReport?) = 12.seconds

    companion object {
        private val logger = KotlinLogging.logger {}

        const val HISTORIC_VALUES_LENGTH = 11
        const val TEMPERATURE_CHART_UNIT = 1.0
        const val HUMIDITY_CHART_UNIT = 5.0
    }

    data class RemoteReport(
        val lastTemperature: Double?,
        val historicalTemperature: List<Double?>,
        val lastHumidity: Double?,
        val historicalHumidity: List<Double?>,
        val isWeakBattery: Boolean?,
    ) {
        init {
            lastHumidity?.let { humidity -> require(humidity in (1.0..100.0)) { "invalid humidity: $humidity" } }
            lastTemperature?.let { temp -> require(temp in (-40.0..45.0)) { "invalid temperature value: $temp" } }
            require(historicalTemperature.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class CurrentReport(
        val cycleLength: Duration,
        val indoorSensor: Pair<Instant, RemoteReport>?,
        val outdoorSensor: Pair<Instant, RemoteReport>?,
    )

    private suspend fun prepareSingleStation(
        report: MeasurementReport,
        oldRemoteReport: RemoteReport?,
        temperatureDbKey: DbMeasurementType,
        humidityDbKey: DbMeasurementType,
        weakBatteryDbKey: DbMeasurementType,
    ): RemoteReport? {
        val humidity = report.values.firstOrNull { it.type == humidityDbKey }?.value?.getOrNull()
        val temperature = report.values.firstOrNull { it.type == temperatureDbKey }?.value?.getOrNull()
        val weakBattery = report.values.firstOrNull { it.type == weakBatteryDbKey }?.value?.getOrNull()

        if (humidity == null && temperature == null && weakBattery == null) {
            return null
        }
        val historicalTemperature =
            databaseLayer.getLastHistoricalValuesByHourAsync(report.timestamp, temperatureDbKey, HISTORIC_VALUES_LENGTH)

        val historicalHumidity =
            databaseLayer.getLastHistoricalValuesByHourAsync(report.timestamp, humidityDbKey, HISTORIC_VALUES_LENGTH)

        return RemoteReport(
            temperature ?: oldRemoteReport?.lastTemperature,
            historicalTemperature.await(),
            humidity ?: oldRemoteReport?.lastHumidity,
            historicalHumidity.await(),
            when (weakBattery ?: oldRemoteReport?.isWeakBattery) {
                1.0 -> true
                null -> null
                else -> false
            }
        )
    }


    override suspend fun onNewMeasurementReport(
        report: MeasurementReport,
        oldStatus: CurrentReport?
    ): UpdateStatus = coroutineScope {
        val indoor = async {
            prepareSingleStation(
                report,
                oldStatus?.indoorSensor?.second,
                IndoorTemperature,
                IndoorHumidity,
                IndoorWeakBattery
            )
        }

        val outdoor = async {
            prepareSingleStation(
                report,
                oldStatus?.outdoorSensor?.second,
                OutdoorTemperature,
                OutdoorHumidity,
                OutdoorWeakBattery
            )
        }

        if (indoor.await() == null && outdoor.await() == null) {
            return@coroutineScope UpdateStatus.NoNewData
        }

        UpdateStatus.NewData(
            CurrentReport(
                report.cycleLength ?: oldStatus?.cycleLength ?: 5.minutes,
                indoor.await()?.let { report.timestamp to it } ?: oldStatus?.indoorSensor,
                outdoor.await()?.let { report.timestamp to it } ?: oldStatus?.outdoorSensor,
            ))
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
                fd.setStaticText(9, "in:")
            }
        }

        if (redrawStatus) {
            val outs = status?.outdoorSensor?.second
            val ins = status?.indoorSensor?.second

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
                status?.indoorSensor?.first ?: Instant.DISTANT_PAST,
                status?.outdoorSensor?.first ?: Instant.DISTANT_PAST,
            ),
            now,
            status?.cycleLength,
        )

        Unit
    }
}