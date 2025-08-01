@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.dataharvesters.SimplemonitorDataHarvester.SimpleMonitorJson
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class SimplemonitorDataSnapshot(
    override val timestamp: Instant,
    override val cycleLength: Duration,
    val data: SimpleMonitorJson?,
) : DataSnapshot {
    override val values: List<DataSample> = emptyList()
}

class SimplemonitorDataHarvester(
    private val config: Config,
    eventBus: DataSnapshotBus,
) : DataHarvester(logger, 90.seconds, eventBus) {

    @Serializable
    enum class MonitorStatus {
        @SerialName("OK")
        OK,

        @SerialName("Fail")
        FAIL,

        @SerialName("Skipped")
        SKIPPED,
    }

    @Serializable
    data class Monitor(
        val status: MonitorStatus,
        @SerialName("virtual_fail_count") val virtualFailCount: Int,
        val result: String,
    )

    @Serializable
    data class SimpleMonitorJson(
        @Serializable(SimpleMonitorInstantSerializer::class)
        val generated: kotlinx.datetime.Instant,
        val monitors: Map<String, Monitor>
    )

    companion object {
        private val logger = KotlinLogging.logger {}

        suspend fun getLatestSimpleMonitorJson(url: URI, user: String?, password: String?): SimpleMonitorJson {

            logger.info { "Downloading SimpleMonitor status from $url." }

            return if (user != null && password != null) {
                val auth = Base64.getEncoder().encodeToString("$user:$password".toByteArray(StandardCharsets.UTF_8))
                httpClient.get {
                    url(url.toString())
                    header("Authorization", "Basic $auth")
                }.body()
            } else {
                httpClient.get(url.toString()).body()
            }
        }
    }

    override suspend fun pollForNewData(now: Instant) {
        publish(
            try {
                val json = getLatestSimpleMonitorJson(
                    config.simplemonitor.url,
                    config.simplemonitor.user, config.simplemonitor.password,
                )

                json.monitors.filterValues { it.status == MonitorStatus.FAIL }.let { failedMons ->
                    if (failedMons.isNotEmpty()) {
                        logger.warn { "${failedMons.size} monitors are failed." }
                    }
                }

                SimplemonitorDataSnapshot(
                    now,
                    pollingInterval,
                    json,
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to download status from SimpleMonitor URL." }
                SimplemonitorDataSnapshot(
                    now,
                    pollingInterval,
                    null,
                )
            })
    }
}