package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.commons.lang3.StringUtils
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SimpleMonitorView(
    private val config: Config,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "SimpleMonitor",
    90.seconds,
    15.seconds,
    8.seconds
) {

    @Serializable
    enum class MonitorStatus {
        @SerialName("OK")
        OK,

        @SerialName("Fail")
        FAIL,

        @SerialName("Skipped")
        SKIPPED
    }

    @Serializable
    data class Monitor(
        val status: MonitorStatus,
        @SerialName("virtual_fail_count") val virtualFailCount: Int,
        val result: String
    )

    @Serializable
    data class SimpleMonitorJson(
        @Serializable(SimpleMonitorInstantSerializer::class)
        val generated: Instant,
        val monitors: Map<String, Monitor>
    )

    data class CurrentReport(
        val timestamp: Instant,
        val data: SimpleMonitorJson?
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

    internal var currentReport: CurrentReport? = null

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {
        val (status, newReport) = try {
            val json = getLatestSimpleMonitorJson(
                config.simplemonitor.url,
                config.simplemonitor.user, config.simplemonitor.password
            )

            json.monitors.filterValues { it.status == MonitorStatus.FAIL }.let { failedMons ->
                if (failedMons.isNotEmpty()) {
                    logger.warn { "${failedMons.size} monitors are failed." }
                }
            }

            UpdateStatus.FULL_SUCCESS to CurrentReport(now, json)
        } catch (e: Exception) {
            logger.error(e) { "Failed to download status from SimpleMonitor URL." }
            UpdateStatus.FAILURE to CurrentReport(now, null)
        }

        currentReport = newReport

        status
    }

    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val report = currentReport
            val fd = hardware.frontDisplay

            if (redrawStatus) {
                fd.clear()

                logger.debug { "Redrawing SimpleMonitor status." }

                fun monitors(status: MonitorStatus) = report?.data?.monitors?.filterValues { it.status == status }
                val failedMonitors = monitors(MonitorStatus.FAIL)

                launch {
                    val time = report?.data?.generated?.toLocalDateTime(TimeZone.currentSystemDefault())
                        ?.toLocalTime()
                        ?.formatJustHoursMinutes()
                        ?: "--:--"
                    fd.setStaticText(0, "*$time")
                }

                launch {
                    val okMonitorsCount = monitors(MonitorStatus.OK)?.size
                    val skippedMonitorsCount = monitors(MonitorStatus.SKIPPED)?.size

                    val allMonitorsCount = report?.data?.monitors?.size

                    val upperText = "OK:${okMonitorsCount ?: "--"}+${
                        skippedMonitorsCount
                            ?: "--"
                    }/${allMonitorsCount ?: "--"}"

                    fd.setStaticText(20 - upperText.length, upperText)
                }

                launch {
                    when (failedMonitors?.size) {
                        null -> fd.setStaticText(21, "SERVER COMM ERROR!")
                        0 -> fd.setStaticText(22, "All monitors OK")
                        else -> {
                            val failedText = "${failedMonitors.size} FAILED"
                            fd.setStaticText(20, failedText)
                            val scrollingText = StringUtils.abbreviate(failedMonitors.keys.joinToString(","), Slot.SLOT0.capacity)
                            fd.setScrollingText(
                                Slot.SLOT0,
                                21 + failedText.length,
                                19 - failedText.length,
                                scrollingText
                            )
                        }
                    }
                }
            }

            drawProgressBar(report?.timestamp, now)

            Unit
        }
}