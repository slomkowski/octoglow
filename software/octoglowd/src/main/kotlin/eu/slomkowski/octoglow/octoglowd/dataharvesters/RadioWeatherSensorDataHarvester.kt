package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.RemoteSensorReport
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RadioWeatherSensorDataHarvester(
    private val config: Config,
    private val hardware: Hardware,
    eventBus: DataSnapshotBus,
) : DataHarvester(logger, 5.seconds, eventBus) {

    companion object {
        private val logger = KotlinLogging.logger { }

        private val MINIMAL_DURATION_BETWEEN_MEASUREMENTS: Duration = 2.minutes

        private val MAXIMAL_DURATION_BETWEEN_MEASUREMENTS: Duration = 5.minutes
    }

    init {
        require(MINIMAL_DURATION_BETWEEN_MEASUREMENTS < MAXIMAL_DURATION_BETWEEN_MEASUREMENTS)
        require(config.remoteSensors.indoorChannelId != config.remoteSensors.outdoorChannelId) { "indoor and outdoor sensors cannot have identical IDs" }
    }

    private val previousReportMap = ConcurrentHashMap<Int, Pair<Instant, RemoteSensorReport>>()

    private fun poolRemoteSensor(
        now: Instant,
        receivedReportResult: Result<RemoteSensorReport>,
        channelId: Int,
        temperatureDbKey: DataSampleType,
        humidityDbKey: DataSampleType,
        weakBatteryDbKey: DataSampleType,
    ): StandardDataSnapshot? {
        if (receivedReportResult.isFailure) {
            val exp = checkNotNull(receivedReportResult.exceptionOrNull())
            return StandardDataSnapshot(
                now, MAXIMAL_DURATION_BETWEEN_MEASUREMENTS,
                listOf(
                    StandardDataSample(temperatureDbKey, Result.failure(exp)),
                    StandardDataSample(humidityDbKey, Result.failure(exp)),
                    StandardDataSample(weakBatteryDbKey, Result.failure(exp))
                )
            )
        }

        val receivedReport = receivedReportResult.getOrThrow()
        if (receivedReport.alreadyReadFlag) {
            return null
        }

        if (channelId != receivedReport.sensorId) {
            return null
        }

        val (previousReportTimestamp, previousReport) = previousReportMap[channelId] ?: (null to null)

        // don't update the report if it is younger than MINIMAL_DURATION_BETWEEN_MEASUREMENTS
        if (now.toEpochMilliseconds() - (previousReportTimestamp ?: Instant.DISTANT_PAST).toEpochMilliseconds() < MINIMAL_DURATION_BETWEEN_MEASUREMENTS.inWholeMilliseconds) {
            logger.debug { "Values for remote sensor (channel $channelId) already received, skipping current report." }
            return null
        }

        previousReportMap[channelId] = now to receivedReport

        return StandardDataSnapshot(
            now, MAXIMAL_DURATION_BETWEEN_MEASUREMENTS,
            listOf(
                StandardDataSample(temperatureDbKey, Result.success(receivedReport.temperature)),
                StandardDataSample(humidityDbKey, Result.success(receivedReport.humidity)),
                StandardDataSample(
                    weakBatteryDbKey, Result.success(
                        when (receivedReport.batteryIsWeak) {
                            true -> 1.0
                            else -> 0.0
                        }
                    )
                )
            )
        )
    }

    override suspend fun pollForNewData(now: Instant): Unit = coroutineScope {
        val remoteSensorReportNullable = try {
            Result.success(hardware.clockDisplay.retrieveRemoteSensorReport())
        } catch (e: Exception) {
            logger.error(e) { "Error retrieving remote sensor report." }
            Result.failure(e)
        }

        if (remoteSensorReportNullable.isSuccess && remoteSensorReportNullable.getOrNull() == null) {
            return@coroutineScope
        }

        val remoteSensorReport = remoteSensorReportNullable.map { checkNotNull(it) }

        listOf(
            async {
                poolRemoteSensor(
                    now,
                    remoteSensorReport,
                    config.remoteSensors.indoorChannelId,
                    IndoorTemperature,
                    IndoorHumidity,
                    IndoorWeakBattery,
                )
            },
            async {
                poolRemoteSensor(
                    now,
                    remoteSensorReport,
                    config.remoteSensors.outdoorChannelId,
                    OutdoorTemperature,
                    OutdoorHumidity,
                    OutdoorWeakBattery
                )
            }).awaitAll().filterNotNull().forEach { publish(it) }
    }
}