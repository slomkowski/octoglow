package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.jacksonObjectMapper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KLogging
import java.net.URL
import java.time.Duration
import java.time.LocalDateTime

class SimpleMonitorView(
        private val config: Config,
        hardware: Hardware)
    : FrontDisplayView(hardware,
        "SimpleMonitor",
        Duration.ofMinutes(1),
        Duration.ofSeconds(15),
        Duration.ofSeconds(7)) {

    enum class MonitorStatus(@JsonValue val jsonName: String) {
        OK("OK"),
        FAIL("Fail"),
        SKIPPED("Skipped")
    }

    data class Monitor(
            val status: MonitorStatus,
            @JsonProperty("virtual_fail_count") val virtualFailCount: Int,
            val result: String)

    data class SimpleMonitorJson(
            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            val generated: LocalDateTime,
            val monitors: Map<String, Monitor>)

    data class CurrentReport(
            val timestamp: LocalDateTime,
            val data: SimpleMonitorJson?)

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

    override suspend fun poolStatusData(): UpdateStatus {
        TODO()
    }

    override suspend fun poolInstantData(): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
        val report = currentReport
        val fd = hardware.frontDisplay

        if (redrawStatus) {
            logger.debug { "Redrawing SimpleMonitor status." }

            launch { fd.setStaticText(0, "Monitors") }

        }

        drawProgressBar(report?.timestamp) //todo

        Unit
    }

}