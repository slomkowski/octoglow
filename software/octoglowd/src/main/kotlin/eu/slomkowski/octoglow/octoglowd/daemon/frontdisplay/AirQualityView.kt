package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mu.KLogging
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class AirQualityView(
    private val config: Config,
    private val database: DatabaseLayer,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "Air quality from powietrze.gios.gov.pl",
    Duration.ofMinutes(10),
    Duration.ofSeconds(15),
    Duration.ofSeconds(13)
) {

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

    private var currentReport: CurrentReport? = null

    companion object : KLogging() {

        private const val HISTORIC_VALUES_LENGTH = 14

        suspend fun retrieveAirQualityData(stationId: Long): AirQualityDto {
            require(stationId > 0)

            logger.debug("Downloading currency rates for station {}.", stationId)
            val url = "http://api.gios.gov.pl/pjp-api/rest/aqindex/getIndex/$stationId"

            val resp: AirQualityDto = httpClient.get(url)

            check(resp.stIndexStatus) { "no air quality index for station ${resp.id}" }
            checkNotNull(resp.stIndexLevel.level) { "no air quality index for station ${resp.id}" }
            logger.debug("Air quality is {}.", resp)

            return resp
        }
    }

    private suspend fun createStationReport(now: Instant, station: AirStationKey) = try {
        val dto = retrieveAirQualityData(station.id)
        val dbKey = AirQuality(station.id)
        val value = dto.stIndexLevel.id.toDouble()
        val timestamp = dto.stSourceDataDate.toJavaInstant().atZone(ZoneId.systemDefault())
        database.insertHistoricalValueAsync(timestamp, dbKey, value)

        val history =
            database.getLastHistoricalValuesByHourAsync(now, dbKey, HISTORIC_VALUES_LENGTH).await()

        AirQualityReport(station.name, checkNotNull(dto.stIndexLevel.level), value, history)
    } catch (e: Exception) {
        logger.error("Failed to update air quality of station ${station.id}.", e)
        null
    }

    override suspend fun poolInstantData(now: ZonedDateTime) = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData(now: ZonedDateTime): UpdateStatus = coroutineScope {
        val instantNow = now.toInstant().toKotlinInstant()

        val rep1 = async { createStationReport(instantNow, config[AirQualityKey.station1]) }
        val rep2 = async { createStationReport(instantNow, config[AirQualityKey.station2]) }

        val newRep = CurrentReport(rep1.await(), rep2.await())

        currentReport = newRep

        newRep.updateStatus
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: ZonedDateTime) =
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