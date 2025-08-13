@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class AirQualityDataSample(
    val stationId: Long, // todo czy to potrzebne?
    val name: String,
    override val type: DataSampleType,
    val level: Result<AirQualityDataHarvester.AirQualityIndex>,
) : DataSample {
    // report może być brak wartości

    override val value: Result<Double> = level.map { it.ordinal.toDouble() }
}

class AirQualityDataHarvester(
    private val config: Config,
    dataSnapshotBus: DataSnapshotBus,
) : DataHarvester(logger, 10.minutes, dataSnapshotBus) {

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
        val aqIndex: AqIndexDataDto,
    ) {
        override fun toString(): String {
            return "{${aqIndex.stationId}, ${aqIndex.level}, ${aqIndex.criticalPollutantCode}}"
        }
    }

    @Serializable
    data class AqIndexDataDto(
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

    companion object {
        private val logger = KotlinLogging.logger {}

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

    private suspend fun createStationReport(now: Instant, station: ConfSingleAirStation): StandardDataSnapshot {
        val dbKey = AirQuality(station.id)

        val report = try {
            val dto = retrieveAirQualityData(station.id)

            AirQualityDataSample(
                station.id,
                station.name,
                dbKey,
                Result.success(checkNotNull(dto.aqIndex.level)),
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to update air quality of station ${station.id}." }
            AirQualityDataSample(
                station.id,
                station.name,
                dbKey,
                Result.failure(e),
            )
        }

        return StandardDataSnapshot(now, pollingInterval, listOf(report))
    }

    override suspend fun pollForNewData(now: Instant): Unit = coroutineScope {
        launch { publish(createStationReport(now, config.airQuality.station1)) }
        launch { publish(createStationReport(now, config.airQuality.station2)) }
    }
}