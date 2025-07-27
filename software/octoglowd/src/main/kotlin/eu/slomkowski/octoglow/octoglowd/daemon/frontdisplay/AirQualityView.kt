package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


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
    data class AirQualityDto(
        @SerialName("AqIndex")
        val aqIndex: AqIndexData,
    ) {
        override fun toString(): String {
            return "{${aqIndex.stationId}, ${aqIndex.level}, ${aqIndex.criticalPollutantCode}}"
        }
    }

    @Serializable
    data class AqIndexData(
        @SerialName("Identyfikator stacji pomiarowej")
        val stationId: Long,

        @SerialName("Status indeksu ogólnego dla stacji pomiarowej")
        val stIndexStatus: Boolean,

        @SerialName("Nazwa kategorii indeksu")
        val stLevelName: String,

        @SerialName("Kod zanieczyszczenia krytycznego")
        val criticalPollutantCode: String,

        @SerialName("Wartość indeksu")
        val indexLevel: Int,

        @SerialName("Data danych źródłowych, z których policzono wartość indeksu dla wskaźnika st")
        @Serializable(AirQualityInstantSerializer::class)
        val sourceDataDate: Instant
    ) {
        val level: AirQualityIndex?
            get() = when (indexLevel) {
                -1 -> null
                else -> AirQualityIndex.entries[indexLevel]
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

            logger.debug { "Downloading air quality data for station $stationId." }
            val url = "https://api.gios.gov.pl/pjp-api/v1/rest/aqindex/getIndex/$stationId"

            try {
                val resp: AirQualityDto = httpClient.get(url) {
                    header(HttpHeaders.Accept, "application/ld+json")
                    timeout {
                        requestTimeoutMillis = 10_000
                    }
                }.body()

                check(resp.aqIndex.stIndexStatus && resp.aqIndex.level != null) { "no air quality index for station ${resp.aqIndex.stationId}" }
                logger.debug { "Air quality is $resp." }

                return resp
            } catch (e: Exception) {
                logger.error(e) { "Failed to retrieve air quality data from $url" }
                throw e
            }
        }
    }

    private suspend fun createStationReport(now: Instant, station: ConfSingleAirStation) = try {
        val dto = retrieveAirQualityData(station.id)
        val dbKey = AirQuality(station.id)
        val value = dto.aqIndex.indexLevel.toDouble()
        database.insertHistoricalValueAsync(dto.aqIndex.sourceDataDate, dbKey, value)

        val history =
            database.getLastHistoricalValuesByHourAsync(now, dbKey, HISTORIC_VALUES_LENGTH).await()

        AirQualityReport(station.name, checkNotNull(dto.aqIndex.level), value, history)
    } catch (e: Exception) {
        logger.error(e) { "Failed to update air quality of station ${station.id}." }
        null
    }

    override suspend fun pollInstantData(now: Instant) = UpdateStatus.NO_NEW_DATA

    override suspend fun pollStatusData(now: Instant): UpdateStatus = coroutineScope {

        // todo zrobić partial success
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