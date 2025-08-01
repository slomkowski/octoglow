@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.datacollectors

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.daemon.PollingDemon
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/*
data collectory
robią poll, publikują wartość we flow


database layer słucha komunikatów typu measurement value
mqtt słucha też tego i wkłada
 */

interface Measurement {
    val type: MeasurementType
    val value: Result<Double>
}

data class StandardMeasurement(
    override val type: MeasurementType,
    override val value: Result<Double>,
) : Measurement

interface MeasurementReport {
    val timestamp: Instant
    val cycleLength: Duration? // todo not null??
    val values: List<Measurement>
}

data class StandardMeasurementReport(
    override val timestamp: Instant,
    override val cycleLength: Duration,
    override val values: List<Measurement>,
) : MeasurementReport {
    init {
        require(values.isNotEmpty()) { "At least one value is required" }
    }
}

data class AirQualityMeasurement(
    val stationId: Long, // todo czy to potrzebne?
    val name: String,
    override val type: MeasurementType,
    val level: Result<AirQualityDataCollector.AirQualityIndex>,
) : Measurement {
    // report może być brak wartości

    override val value: Result<Double> = level.map { it.ordinal.toDouble() }
}

abstract class DataCollector(
    logger: KLogger,
    pollInterval: Duration,
    private val eventBus: HistoricalValuesEvents,
) : PollingDemon(logger, pollInterval) {

    abstract suspend fun pollForNewData(now: Instant)

    protected suspend fun publishPacket(packet: MeasurementReport) {
        eventBus.produceEvent(packet)
    }

    final override suspend fun poll() {
        // todo przekazywać jakoś czas z zewnątrz?
        pollForNewData(Clock.System.now())
    }
}

class AirQualityDataCollector(
    private val config: Config,
    historicalValuesBus: HistoricalValuesEvents,
) : DataCollector(logger, 10.minutes, historicalValuesBus) {
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
        val sourceDataDate: kotlinx.datetime.Instant
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

    private suspend fun createStationReport(now: Instant, station: ConfSingleAirStation): StandardMeasurementReport {
        val dbKey = AirQuality(station.id)

        val report = try {
            val dto = retrieveAirQualityData(station.id)

            AirQualityMeasurement(
                station.id,
                station.name,
                dbKey,
                Result.success(checkNotNull(dto.aqIndex.level)),
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to update air quality of station ${station.id}." }
            AirQualityMeasurement(
                station.id,
                station.name,
                dbKey,
                Result.failure(e),
            )
        }

        return StandardMeasurementReport(now, pollInterval, listOf(report))
    }

    override suspend fun pollForNewData(now: Instant): Unit = coroutineScope {
        launch { publishPacket(createStationReport(now, config.airQuality.station1)) }
        launch { publishPacket(createStationReport(now, config.airQuality.station2)) }
    }
}