package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KLogging
import org.apache.commons.lang3.StringUtils
import java.net.URL
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class SimpleMonitorView(
    private val config: Config,
    private val database: DatabaseLayer,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "SimpleMonitor",
    Duration.ofSeconds(90),
    Duration.ofSeconds(15),
    Duration.ofSeconds(8)
) {

    enum class MonitorStatus(@JsonValue val jsonName: String) {
        OK("OK"),
        FAIL("Fail"),
        SKIPPED("Skipped")
    }

    data class Monitor(
        val status: MonitorStatus,
        @JsonProperty("virtual_fail_count") val virtualFailCount: Int,
        val result: String
    )

    data class SimpleMonitorJson(
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ssxxx")
        val generated: OffsetDateTime,
        val monitors: Map<String, Monitor>
    )

    data class CurrentReport(
        val timestamp: ZonedDateTime,
        val data: SimpleMonitorJson?
    )

    companion object : KLogging() {
        suspend fun getLatestSimpleMonitorJson(url: URL, user: String?, password: String?): SimpleMonitorJson {

            val request = if (user != null && password != null) {
                Fuel.get(url.toString()).authentication().basic(user, password)
            } else {
                Fuel.get(url.toString())
            }

            logger.info { "Downloading SimpleMonitor status from $url." }

            return request.awaitObject(jacksonDeserializerOf(jacksonObjectMapper))
        }
    }

    private var currentReport: CurrentReport? = null

    override suspend fun poolStatusData(now: ZonedDateTime): UpdateStatus = coroutineScope {
        val (status, newReport) = try {
            val json = getLatestSimpleMonitorJson(
                config[SimpleMonitorKey.url],
                config[SimpleMonitorKey.user], config[SimpleMonitorKey.password]
            )

            if (json.monitors.filterValues { it.status == MonitorStatus.FAIL }.isNotEmpty()) {
                logger.warn { "Some monitors are failed." }
                launch {
                    val shouldRing =
                        database.getChangeableSettingAsync(ChangeableSetting.SIMPLEMONITOR_RING_ON_FAILURE).await()
                    if (shouldRing != "false") {
                        ringBellIfNotSleeping(config, logger, hardware, Duration.ofMillis(100))
                    } else {
                        logger.warn { "Ringing on failure is disabled." }
                    }
                }
            }

            UpdateStatus.FULL_SUCCESS to CurrentReport(now, json)
        } catch (e: Exception) {
            logger.error(e) { "Failed to download status from Simplemonitor URL." }
            UpdateStatus.FAILURE to CurrentReport(now, null)
        }

        currentReport = newReport

        status
    }

    override suspend fun poolInstantData(now: ZonedDateTime): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: ZonedDateTime) =
        coroutineScope {
            val report = currentReport
            val fd = hardware.frontDisplay

            if (redrawStatus) {
                fd.clear()

                logger.debug { "Redrawing SimpleMonitor status." }

                fun monitors(status: MonitorStatus) = report?.data?.monitors?.filterValues { it.status == status }
                val failedMonitors = monitors(MonitorStatus.FAIL)

                launch {
                    val time = report?.data?.generated?.toLocalDateTimeInSystemTimeZone()
                        ?.format(DateTimeFormatter.ofPattern("HH:mm"))
                        ?: "--:--"
                    fd.setStaticText(0, "@ $time")
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
                            val scrollingText = StringUtils.abbreviate(failedMonitors.keys.joinToString(","), 150)
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

    override fun getMenus(): List<Menu> = listOf(
        BooleanChangeableSettingMenu(
            database,
            ChangeableSetting.SIMPLEMONITOR_RING_ON_FAILURE,
            "Ring on bad mon."
        )
    )
}