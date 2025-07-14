package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Scd40measurements
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class LocalSensorView(
    private val config: Config,
    private val databaseLayer: DatabaseLayer,
    hardware: Hardware,
) : FrontDisplayView(
    hardware,
    "BME280 and SCD40 data",
    30.seconds,
    7.seconds,
) {
    override val preferredDisplayTime: Duration = 12.seconds

    companion object {
        private val logger = KotlinLogging.logger {}

        const val HISTORIC_VALUES_LENGTH = 11
        const val TEMPERATURE_CHART_UNIT = 1.0
        const val HUMIDITY_CHART_UNIT = 5.0
    }

    //todo wykresy, zrobić widok na pięknie
    data class CurrentReport(
        val ts: Instant,
        val bme280temperature: Double,
        val bme280humidity: Double,
        val scd40temperature: Double,
        val scd40humidity: Double,
        val mslPressure: Double,
        val realPressure: Double,
        val co2concentration: Double,
    )

    // todo rysowanie progress bar
    @Volatile
    var currentReport: CurrentReport? = null

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val rep = currentReport
            val fd = hardware.frontDisplay

            if (redrawStatus) {
                fd.setStaticText(0, formatHumidity(rep?.bme280humidity))
                fd.setStaticText(4, formatTemperature(rep?.bme280temperature))
                fd.setStaticText(12, formatPressure(rep?.mslPressure))

                fd.setStaticText(20, formatHumidity(rep?.scd40humidity))
                fd.setStaticText(24, formatTemperature(rep?.scd40temperature))
                fd.setStaticText(32, formatPpmConcentration(rep?.co2concentration))
            }

            Unit
        }

    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {
        val ts = now()
        val oldReport = currentReport

        val bme280data = hardware.bme280.readReport()
        val scd40dataAsync = async { hardware.scd40.readMeasurementWithWaiting() }
        val lightSensorMeasurementAsync = async { hardware.clockDisplay.retrieveLightSensorMeasurement() }

        val elevation = config.geoPosition.elevation
        val mslPressure = bme280data.getMeanSeaLevelPressure(elevation)

        val bme280dbJobs = listOf(
            databaseLayer.insertHistoricalValueAsync(ts, Bme280Temperature, bme280data.temperature),
            databaseLayer.insertHistoricalValueAsync(ts, Bme280Humidity, bme280data.humidity),
            databaseLayer.insertHistoricalValueAsync(ts, RealPressure, bme280data.realPressure),
            databaseLayer.insertHistoricalValueAsync(ts, MSLPressure, mslPressure),
        )

        logger.info { "Got BMP280 report : ${bme280data}." }
        logger.info { "Mean sea-level pressure at $elevation m is $mslPressure hPa." }

        val scd40data: Scd40measurements = scd40dataAsync.await()

        if (oldReport?.mslPressure == null || bme280data.realPressure != oldReport.realPressure) {
            launch {
                hardware.scd40.setAmbientPressure(bme280data.realPressure)
            }
        }

        logger.info { "Got SCD40 report : ${scd40data}." }

        val scd40dbJobs = listOf(
            databaseLayer.insertHistoricalValueAsync(ts, IndoorCo2, scd40data.co2),
            databaseLayer.insertHistoricalValueAsync(ts, Scd40Humidity, scd40data.humidity),
            databaseLayer.insertHistoricalValueAsync(ts, Scd40Temperature, scd40data.temperature),
        )

        bme280dbJobs
            .plus(scd40dbJobs)
            .plus(databaseLayer.insertHistoricalValueAsync(ts, LightSensorValue, lightSensorMeasurementAsync.await().toDouble()))
            .joinAll()

        currentReport = CurrentReport(
            ts = now,
            bme280temperature = bme280data.temperature,
            bme280humidity = bme280data.humidity,
            scd40temperature = scd40data.temperature,
            scd40humidity = scd40data.humidity,
            mslPressure = mslPressure,
            realPressure = bme280data.realPressure,
            co2concentration = scd40data.co2,
        )

        UpdateStatus.FULL_SUCCESS
    }

}