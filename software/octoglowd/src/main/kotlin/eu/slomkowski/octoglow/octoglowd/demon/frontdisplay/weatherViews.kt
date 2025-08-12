@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.Instant.Companion.DISTANT_PAST

abstract class AbstractWeatherView<StatusType>(
    private val config: Config,
    private val database: DatabaseDemon,
    hardware: Hardware,
    name: String,
) : FrontDisplayView<StatusType, Unit>(
    hardware,
    name,
    null,
    logger,
) {
    override fun preferredDisplayTime(status: StatusType?) = 12.seconds

    data class RadioWeatherSensorReport(
        val timestamp: Instant,
        val cycleLength: Duration,
        val temperature: CurrentAndHistorical,
        val humidity: CurrentAndHistorical,
        val isWeakBattery: Boolean?,
    ) {
        init {
            humidity.lastValue?.let { h -> require(h in (1.0..100.0)) { "invalid humidity: $h" } }
            temperature.lastValue?.let { t -> require(t in (-40.0..45.0)) { "invalid temperature value: $t" } }
            temperature.historicalValues?.let { require(it.size == HISTORIC_VALUES_LENGTH) }
        }
    }

    data class CurrentAndHistorical(
        val timestamp: Instant,
        val lastValue: Double?,
        val historicalValues: List<Double?>?,
    )

    protected suspend fun prepareSingleStation(
        report: DataSnapshot,
        oldRemoteReport: RadioWeatherSensorReport?,
        temperatureDbKey: DbDataSampleType,
        humidityDbKey: DbDataSampleType,
        weakBatteryDbKey: DbDataSampleType,
    ): RadioWeatherSensorReport? {
        val humidity = report.values.firstOrNull { it.type == humidityDbKey }?.value?.getOrNull()
        val temperature = report.values.firstOrNull { it.type == temperatureDbKey }?.value?.getOrNull()
        val weakBattery = report.values.firstOrNull { it.type == weakBatteryDbKey }?.value?.getOrNull()

        if (humidity == null && temperature == null && weakBattery == null) {
            return null
        }
        val historicalTemperature =
            database.getLastHistoricalValuesByHourAsync(report.timestamp, temperatureDbKey, HISTORIC_VALUES_LENGTH)

        val historicalHumidity =
            database.getLastHistoricalValuesByHourAsync(report.timestamp, humidityDbKey, HISTORIC_VALUES_LENGTH)

        return RadioWeatherSensorReport(
            report.timestamp,
            report.cycleLength,
            CurrentAndHistorical(
                report.timestamp,
                temperature ?: oldRemoteReport?.temperature?.lastValue,
                historicalTemperature.await()
            ),
            CurrentAndHistorical(
                report.timestamp,
                humidity ?: oldRemoteReport?.humidity?.lastValue,
                historicalHumidity.await()
            ),
            when (weakBattery ?: oldRemoteReport?.isWeakBattery) {
                1.0 -> true
                null -> null
                else -> false
            }
        )
    }

    companion object {
        protected val logger = KotlinLogging.logger {}

        const val HISTORIC_VALUES_LENGTH = 5 * 3 - 1
        const val TEMPERATURE_CHART_UNIT = 1.0
        const val CO2_CHART_UNIT = 100.0
        const val PRESSURE_CHART_UNIT = 10.0
        const val HUMIDITY_CHART_UNIT = 5.0
    }
}

class IndoorWeatherView(
    config: Config,
    private val database: DatabaseDemon,
    hardware: Hardware,
) : AbstractWeatherView<IndoorWeatherView.CurrentReport>(
    config,
    database,
    hardware,
    "Indoor weather",
) {
    companion object {
        fun formatCo2(v: Double?): String = when (v) {
            null -> "---- ppm"
            in 0.0..5000.0 -> String.format("%4.0f ppm", v)
            else -> String.format(">5000ppm", v)
        }
    }

    data class CurrentReport(
        val timestamp: Instant,
        val cycleLength: Duration,
        val remoteSensor: RadioWeatherSensorReport?,
        val co2: CurrentAndHistorical?,
        val bme280humidity: CurrentAndHistorical?,
        val scd40humidity: CurrentAndHistorical?,
    ) {
        val minTimestamp: Instant? = listOfNotNull(
            remoteSensor?.temperature,
            averageHumidity,
            co2,
            bme280humidity,
            scd40humidity,
        ).minOfOrNull { it.timestamp }

        val averageHumidity: CurrentAndHistorical
            get() {
                val currentAveragedValue = listOfNotNull(
                    remoteSensor?.humidity?.lastValue,
                    bme280humidity?.lastValue,
                    scd40humidity?.lastValue,
                ).takeIf { it.isNotEmpty() }?.average()

                val minTimestamp = listOf(
                    remoteSensor?.humidity?.timestamp ?: DISTANT_PAST,
                    bme280humidity?.timestamp ?: DISTANT_PAST,
                    scd40humidity?.timestamp ?: DISTANT_PAST,
                ).min()

                val historicalValues = listOfNotNull(
                    remoteSensor?.humidity?.historicalValues,
                    bme280humidity?.historicalValues,
                    scd40humidity?.historicalValues,
                )

                check(historicalValues.all { it.size == HISTORIC_VALUES_LENGTH })

                return CurrentAndHistorical(
                    minTimestamp,
                    currentAveragedValue,
                    (0..<HISTORIC_VALUES_LENGTH).map { index ->
                        historicalValues.mapNotNull { it[index] }.takeIf { it.isNotEmpty() }?.average()
                    })
            }
    }

    override suspend fun onNewDataSnapshot(
        snapshot: Snapshot,
        oldStatus: CurrentReport?,
    ): UpdateStatus = coroutineScope {
        if (snapshot !is DataSnapshot) {
            return@coroutineScope UpdateStatus.NoNewData
        }

        val bme280humidityNew = snapshot.values.find { it.type == Bme280Humidity }?.value?.getOrNull()
        val scd40humidityNew = snapshot.values.find { it.type == Scd40Humidity }?.value?.getOrNull()
        val co2concentrationNew = snapshot.values.find { it.type == IndoorCo2 }?.value?.getOrNull()

        val indoorNew = async {
            prepareSingleStation(
                snapshot,
                oldStatus?.remoteSensor,
                IndoorTemperature,
                IndoorHumidity,
                IndoorWeakBattery,
            )
        }

        val bme280cah = async {
            bme280humidityNew?.let {
                CurrentAndHistorical(
                    snapshot.timestamp,
                    it,
                    database.getLastHistoricalValuesByHourAsync(snapshot.timestamp, Bme280Humidity, HISTORIC_VALUES_LENGTH).await()
                )
            }
        }

        val scd40cah = async {
            scd40humidityNew?.let {
                CurrentAndHistorical(
                    snapshot.timestamp,
                    it,
                    database.getLastHistoricalValuesByHourAsync(snapshot.timestamp, Scd40Humidity, HISTORIC_VALUES_LENGTH).await()
                )
            }
        }

        val co2cah = async {
            co2concentrationNew?.let {
                CurrentAndHistorical(
                    snapshot.timestamp,
                    it,
                    database.getLastHistoricalValuesByHourAsync(snapshot.timestamp, IndoorCo2, HISTORIC_VALUES_LENGTH).await()
                )
            }
        }

        if (bme280humidityNew == null
            && scd40humidityNew == null
            && co2concentrationNew == null
            && indoorNew.await() == null
        ) {
            return@coroutineScope UpdateStatus.NoNewData
        }

        return@coroutineScope UpdateStatus.NewData(
            CurrentReport(
                snapshot.timestamp,
                snapshot.cycleLength,
                indoorNew.await() ?: oldStatus?.remoteSensor,
                co2cah.await() ?: oldStatus?.co2,
                bme280cah.await() ?: oldStatus?.bme280humidity,
                scd40cah.await() ?: oldStatus?.scd40humidity
            )
        )
    }

    // temperature, humidity, co2
    // humidity is avg between - remote, co2 sensor, bme280 sensor
    // temp is from remote
    // co2 wiadomo
    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: CurrentReport?,
        instant: Unit?,
    ) {
        val fd = hardware.frontDisplay

        // IN
        // humidity
        // temp
        // co2

        if (redrawStatic) {
            fd.setStaticText(0, "Indoor")
        }

        if (redrawStatus) {
            if (status?.remoteSensor?.isWeakBattery == true) {
                fd.setStaticText(6, "!")
            }

            fd.setStaticText(9, formatTemperature(status?.remoteSensor?.temperature?.lastValue))
            fd.setStaticText(20, formatHumidity(status?.averageHumidity?.lastValue))
            fd.setStaticText(28, formatCo2(status?.co2?.lastValue))

            status?.remoteSensor?.temperature?.let {
                fd.setOneLineDiffChart(
                    5 * 17,
                    it.lastValue,
                    it.historicalValues,
                    TEMPERATURE_CHART_UNIT,
                )
            }

            status?.averageHumidity?.let {
                fd.setOneLineDiffChart(
                    5 * 24,
                    it.lastValue,
                    it.historicalValues,
                    HUMIDITY_CHART_UNIT,
                )
            }

            status?.co2?.let {
                fd.setOneLineDiffChart(
                    5 * 37,
                    it.lastValue,
                    it.historicalValues,
                    CO2_CHART_UNIT,
                )
            }
        }

        drawProgressBar(
            status?.minTimestamp ?: DISTANT_PAST,
            now,
            status?.cycleLength,
        )
    }
}

class OutdoorWeatherView(
    config: Config,
    private val database: DatabaseDemon,
    hardware: Hardware,
) : AbstractWeatherView<OutdoorWeatherView.CurrentReport>(
    config,
    database,
    hardware,
    "Outdoor weather",
) {
    data class CurrentReport(
        val timestamp: Instant,
        val cycleLength: Duration,
        val remoteSensor: RadioWeatherSensorReport?,
        val pressure: CurrentAndHistorical?,
    ) {
        val minTimestamp: Instant? = listOfNotNull(
            remoteSensor?.temperature,
            remoteSensor?.humidity,
            pressure,
        ).minOfOrNull { it.timestamp }
    }

    override suspend fun onNewDataSnapshot(
        snapshot: Snapshot,
        oldStatus: CurrentReport?,
    ): UpdateStatus = coroutineScope {
        if (snapshot !is DataSnapshot) {
            return@coroutineScope UpdateStatus.NoNewData
        }

        val pressureNew = snapshot.values.find { it.type == MSLPressure }?.value?.getOrNull()

        val outdoorSensorNew = async {
            prepareSingleStation(
                snapshot,
                oldStatus?.remoteSensor,
                OutdoorTemperature,
                OutdoorHumidity,
                OutdoorWeakBattery,
            )
        }

        val pressureCah = async {
            pressureNew?.let {
                CurrentAndHistorical(
                    snapshot.timestamp,
                    it,
                    database.getLastHistoricalValuesByHourAsync(snapshot.timestamp, MSLPressure, HISTORIC_VALUES_LENGTH).await()
                )
            }
        }

        if (pressureNew == null
            && outdoorSensorNew.await() == null
        ) {
            return@coroutineScope UpdateStatus.NoNewData
        }

        return@coroutineScope UpdateStatus.NewData(
            CurrentReport(
                snapshot.timestamp,
                snapshot.cycleLength,
                outdoorSensorNew.await() ?: oldStatus?.remoteSensor,
                pressureCah.await() ?: oldStatus?.pressure,
            )
        )
    }

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: CurrentReport?,
        instant: Unit?,
    ) {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            fd.setStaticText(0, "Outdoor")
        }

        if (redrawStatus) {
            if (status?.remoteSensor?.isWeakBattery == true) {
                fd.setStaticText(7, "!")
            }

            fd.setStaticText(9, formatTemperature(status?.remoteSensor?.temperature?.lastValue))
            fd.setStaticText(20, formatHumidity(status?.remoteSensor?.humidity?.lastValue))
            fd.setStaticText(28, formatPressure(status?.pressure?.lastValue))

            status?.remoteSensor?.temperature?.let {
                fd.setOneLineDiffChart(
                    5 * 17,
                    it.lastValue,
                    it.historicalValues,
                    TEMPERATURE_CHART_UNIT,
                )
            }

            status?.remoteSensor?.humidity?.let {
                fd.setOneLineDiffChart(
                    5 * 24,
                    it.lastValue,
                    it.historicalValues,
                    HUMIDITY_CHART_UNIT,
                )
            }

            status?.pressure?.let {
                fd.setOneLineDiffChart(
                    5 * 37,
                    it.lastValue,
                    it.historicalValues,
                    PRESSURE_CHART_UNIT,
                )
            }
        }

        drawProgressBar(
            status?.minTimestamp ?: DISTANT_PAST,
            now,
            status?.cycleLength,
        )
    }
}