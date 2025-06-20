package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class AirQualityView(
    private val config: Config,
    private val database: DatabaseLayer,
    hardware: Hardware,
) : FrontDisplayView(
    hardware,
    "Air quality from powietrze.gios.gov.pl",
    10.minutes,
    15.seconds,
) {
    override val preferredDisplayTime: Duration = 13.seconds

    enum class AirQualityIndex(val text: String) {
        EXCELLENT("excellent"),
        GOOD("good"),
        FAIR("fair"),
        POOR("poor"),
        BAD("BAD!"),
        HAZARDOUS("HAZARDOUS!!")
    }

    @Serializable
    data class StIndexLevel(
        @SerialName("id")
        val id: Int,

        @SerialName("indexLevelName")
        val levelName: String
    ) {
        val level: AirQualityIndex?
            get() = when (id) {
                -1 -> null
                else -> AirQualityIndex.values()[id]
            }
    }

    @Serializable
    data class AirQualityDto(
        val id: Long,
        val stIndexStatus: Boolean,
        val stIndexCrParam: String,
        val stIndexLevel: StIndexLevel,

        @Serializable(AirQualityInstantSerializer::class)
        val stCalcDate: Instant,

        @Serializable(AirQualityInstantSerializer::class)
        val stSourceDataDate: Instant
    ) {
        override fun toString(): String {
            return "{$id, ${stIndexLevel.level}, $stIndexCrParam}"
        }
    }

    data class AirQualityReport(
        val name: String,
        val level: AirQualityIndex,
        val latest: Double,
        val historical: List<Double?>
    ) {
        init {
            require(name.isNotBlank())
            //todo tests for name
        }
    }

    data class CurrentReport(
        val station1: AirQualityReport?,
        val station2: AirQualityReport?
    ) {
        val updateStatus: UpdateStatus
            get() = if (station1 == null && station2 == null) {
                UpdateStatus.FAILURE
            } else if (station1 != null && station2 != null) {
                UpdateStatus.FULL_SUCCESS
            } else {
                UpdateStatus.PARTIAL_SUCCESS
            }
    }

    @Volatile
    private var currentReport: CurrentReport? = null

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val HISTORIC_VALUES_LENGTH = 14

        suspend fun retrieveAirQualityData(stationId: Long): AirQualityDto {
            require(stationId > 0)

            logger.debug { "Downloading currency rates for station $stationId." }
            val url = "https://api.gios.gov.pl/pjp-api/rest/aqindex/getIndex/$stationId"

            val resp: AirQualityDto = httpClient.get(url).body()

            check(resp.stIndexStatus) { "no air quality index for station ${resp.id}" }
            checkNotNull(resp.stIndexLevel.level) { "no air quality index for station ${resp.id}" }
            logger.debug { "Air quality is $resp." }

            return resp
        }
    }

    private suspend fun createStationReport(now: Instant, station: ConfSingleAirStation) = try {
        val dto = retrieveAirQualityData(station.id)
        val dbKey = AirQuality(station.id)
        val value = dto.stIndexLevel.id.toDouble()
        database.insertHistoricalValueAsync(dto.stSourceDataDate, dbKey, value)

        val history =
            database.getLastHistoricalValuesByHourAsync(now, dbKey, HISTORIC_VALUES_LENGTH).await()

        AirQualityReport(station.name, checkNotNull(dto.stIndexLevel.level), value, history)
    } catch (e: Exception) {
        logger.error(e) { "Failed to update air quality of station ${station.id}." }
        null
    }

    override suspend fun poolInstantData(now: Instant) = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {

        val rep1 = async { createStationReport(now, config.airQuality.station1) }
        val rep2 = async { createStationReport(now, config.airQuality.station2) }

        val newRep = CurrentReport(rep1.await(), rep2.await())

        currentReport = newRep

        newRep.updateStatus
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {

            val fd = hardware.frontDisplay
            val rep = currentReport

            launch {
                fd.setStaticText(0, "A")
                fd.setStaticText(20, "Q")
            }

            fun drawForStation(offset: Int, slot: Slot, report: AirQualityReport?) {
                report?.let {
                    launch { fd.setOneLineDiffChart(5 * (offset + 16), it.latest, it.historical, 1.0) }
                }

                launch {
                    fd.setScrollingText(
                        slot,
                        offset + 2,
                        13,
                        report?.let { "${it.name}: ${it.level.text}" } ?: "no air quality data"
                    )
                    fd.setStaticText(offset + 19, report?.level?.ordinal?.toString() ?: "-")
                }
            }

            drawForStation(0, Slot.SLOT0, rep?.station1)
            drawForStation(20, Slot.SLOT1, rep?.station2)
        }

}