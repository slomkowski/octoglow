package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class LocalSensorView(
    private val config: Config,
    private val databaseLayer: DatabaseLayer,
    hardware: Hardware
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

        private val MINIMAL_DURATION_BETWEEN_MEASUREMENTS: Duration = 2.minutes

        private val MAXIMAL_DURATION_BETWEEN_MEASUREMENTS: Duration = 5.minutes
    }

    init {
        require(MINIMAL_DURATION_BETWEEN_MEASUREMENTS < MAXIMAL_DURATION_BETWEEN_MEASUREMENTS)
        require(config.remoteSensors.indoorChannelId != config.remoteSensors.outdoorChannelId) { "indoor and outdoor sensors cannot have identical IDs" }
    }

    data class CurrentReport(
        val temperature: Double,
        val humidity: Double,
        val pressure: Double,
    )

    @Volatile
    var currentReport: CurrentReport? = null

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val rep = currentReport
            val fd = hardware.frontDisplay

            if (redrawStatus) {
                fd.setStaticText(1, formatHumidity(rep?.humidity))
                fd.setStaticText(10, formatTemperature(rep?.temperature))
                fd.setStaticText(30, formatPressure(rep?.pressure))
            }

            Unit
        }

    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {
        val ts = now()

        val rep = hardware.bme280.readReport()

        val elevation = config.geoPosition.elevation
        val mslPressure = rep.getMeanSeaLevelPressure(elevation)

        logger.info { "Got BMP280 report : $rep." }
        logger.info { "Mean sea-level pressure at $elevation m is $mslPressure hPa." }

        listOf(
            databaseLayer.insertHistoricalValueAsync(ts, Bme280Temperature, rep.temperature),
            databaseLayer.insertHistoricalValueAsync(ts, Bme280Humidity, rep.humidity),
            databaseLayer.insertHistoricalValueAsync(ts, RealPressure, rep.realPressure),
            databaseLayer.insertHistoricalValueAsync(ts, MSLPressure, mslPressure)
        ).joinAll()

        currentReport = CurrentReport(
            rep.temperature,
            rep.humidity,
            mslPressure,
        )

        UpdateStatus.FULL_SUCCESS
    }

}