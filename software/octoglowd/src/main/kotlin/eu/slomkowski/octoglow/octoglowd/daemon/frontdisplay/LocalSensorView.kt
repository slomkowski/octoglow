@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.datacollectors.MeasurementReport
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


class LocalSensorView(
    private val config: Config,
    private val databaseLayer: DatabaseLayer,
    hardware: Hardware,
) : FrontDisplayView2<LocalSensorView.CurrentReport, Unit>(
    hardware,
    "BME280 and SCD40 data",
    null,
    logger,
) {
    override val preferredDisplayTime: Duration = 12.seconds

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    //todo wykresy, zrobić widok na pięknie
    data class CurrentReport(
        val timestamp: Instant,
        val cycleLength: Duration?,
        val bme280temperature: Double?,
        val bme280humidity: Double?,
        val scd40temperature: Double?,
        val scd40humidity: Double?,
        val mslPressure: Double?,
        val realPressure: Double?,
        val co2concentration: Double?,
    )

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: CurrentReport?,
        instant: Unit?
    ): Unit = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatus) {
            fd.setStaticText(0, formatHumidity(status?.bme280humidity))
            fd.setStaticText(4, formatTemperature(status?.bme280temperature))
            fd.setStaticText(12, formatPressure(status?.mslPressure))

            fd.setStaticText(20, formatHumidity(status?.scd40humidity))
            fd.setStaticText(24, formatTemperature(status?.scd40temperature))
            fd.setStaticText(32, formatPpmConcentration(status?.co2concentration))
        }

        drawProgressBar(status?.timestamp, now, status?.cycleLength)
    }

    override suspend fun onNewMeasurementReport(report: MeasurementReport, oldStatus: CurrentReport?): UpdateStatus {

        val bme280temperature = report.values.find { it.type == Bme280Temperature }?.value?.getOrNull()
        val bme280humidity = report.values.find { it.type == Bme280Humidity }?.value?.getOrNull()
        val scd40temperature = report.values.find { it.type == Scd40Temperature }?.value?.getOrNull()
        val scd40humidity = report.values.find { it.type == Scd40Humidity }?.value?.getOrNull()
        val mslPressure = report.values.find { it.type == MSLPressure }?.value?.getOrNull()
        val realPressure = report.values.find { it.type == RealPressure }?.value?.getOrNull()
        val co2concentration = report.values.find { it.type == IndoorCo2 }?.value?.getOrNull()

        if (listOf(
                bme280temperature, bme280humidity, scd40temperature, scd40humidity,
                mslPressure, realPressure, co2concentration
            ).all { it == null }
        ) {
            return UpdateStatus.NoNewData
        }

        val newReport = CurrentReport(
            timestamp = report.timestamp,
            cycleLength = report.cycleLength ?: oldStatus?.cycleLength,
            bme280temperature = bme280temperature ?: oldStatus?.bme280temperature,
            bme280humidity = bme280humidity ?: oldStatus?.bme280humidity,
            scd40temperature = scd40temperature ?: oldStatus?.scd40temperature,
            scd40humidity = scd40humidity ?: oldStatus?.scd40humidity,
            mslPressure = mslPressure ?: oldStatus?.mslPressure,
            realPressure = realPressure ?: oldStatus?.realPressure,
            co2concentration = co2concentration ?: oldStatus?.co2concentration,
        )

        return UpdateStatus.NewData(newReport)
    }
}