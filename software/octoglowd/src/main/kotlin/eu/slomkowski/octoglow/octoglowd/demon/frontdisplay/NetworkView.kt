@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.dataharvesters.NetworkDataHarvester
import eu.slomkowski.octoglow.octoglowd.dataharvesters.NetworkDataSnapshot
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class NetworkView(
    hardware: Hardware
) : FrontDisplayView<NetworkView.CurrentReport, Unit>(
    hardware,
    "Network",
    null,
    logger,
) {
    override fun preferredDisplayTime(status: CurrentReport?) = 5.seconds

    data class CurrentReport(
        val timestamp: Instant,
        val cycleLength: Duration,
        val interfaceInfo: TimestampedObject<NetworkDataHarvester.InterfaceInfo?>?,
        val remotePing: TimestampedObject<Duration?>?,
        val gwPing: TimestampedObject<Duration?>?,
        val mqttConnected: Boolean,
    )

    companion object {
        private val logger = KotlinLogging.logger {}

        private val pingMaxAge = 1.minutes

        fun formatPingRtt(now: Instant, d: TimestampedObject<Duration?>?): String {
            if (now - (d?.timestamp ?: Instant.DISTANT_PAST) > pingMaxAge || d?.obj == null) {
                return " -- ms"
            }

            return when (val ms = d.obj.inWholeMilliseconds) {
                0L -> " <1 ms"
                in 1..99 -> String.format(" %2d ms", ms)
                in 100..999 -> String.format(" %3dms", ms)
                else -> ">999ms"
            }
        }

        fun formatInterfaceInfo(now: Instant, interfaceInfo: TimestampedObject<NetworkDataHarvester.InterfaceInfo?>?): String {
            val status = interfaceInfo?.takeIf { now - it.timestamp <= pingMaxAge }?.obj

            return (when (status?.isWifi) {
                false -> "eth"
                true -> "wl"
                else -> ""
            } + " IP: " + (status?.ip?.hostAddress ?: "---.---.---.---")).trim()
        }
    }

    override suspend fun onNewDataSnapshot(
        snapshot: Snapshot,
        oldStatus: CurrentReport?
    ): UpdateStatus {

        if (snapshot is MqttConnectionChanged) {
            return UpdateStatus.NewData(
                CurrentReport(
                    snapshot.timestamp,
                    oldStatus?.cycleLength ?: 5.minutes,
                    oldStatus?.interfaceInfo,
                    oldStatus?.remotePing,
                    oldStatus?.gwPing,
                    snapshot.connected,
                )
            )
        }

        if (snapshot !is DataSnapshot) {
            return UpdateStatus.NoNewData
        }

        if (snapshot is NetworkDataSnapshot && snapshot.interfaceInfo == null) {
            return UpdateStatus.NewData(null)
        }

        val newRemotePing = snapshot.values
            .firstOrNull { it.type == PingTimeRemoteHost }?.value
            ?.let { TimestampedObject(snapshot.timestamp, it.getOrNull()?.milliseconds) }

        val newGwPing = snapshot.values
            .firstOrNull { it.type == PingTimeGateway }?.value
            ?.let { TimestampedObject(snapshot.timestamp, it.getOrNull()?.milliseconds) }

        val interfaceInfo = (snapshot as? NetworkDataSnapshot)?.interfaceInfo?.let { TimestampedObject<NetworkDataHarvester.InterfaceInfo?>(snapshot.timestamp, it) }

        return UpdateStatus.NewData(
            CurrentReport(
                snapshot.timestamp,
                snapshot.cycleLength,
                interfaceInfo ?: oldStatus?.interfaceInfo,
                newRemotePing ?: oldStatus?.remotePing,
                newGwPing ?: oldStatus?.gwPing,
                oldStatus?.mqttConnected ?: false,
            )
        )
    }

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: CurrentReport?,
        instant: Unit?,
    ): Unit = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            fd.setStaticText(0, "ping")
            fd.setStaticText(11, "gw")
            fd.setStaticText(20, "mqtt")
        }

        if (redrawStatus) {
            fd.setStaticText(4, formatPingRtt(now, status?.remotePing))
            fd.setStaticText(14, formatPingRtt(now, status?.gwPing))

            fd.setStaticText(
                25, when (status?.mqttConnected) {
                    true -> "OK   "
                    false -> "FAIL!"
                    null -> "---  "
                }
            )
            fd.setScrollingText(
                Slot.SLOT0, 31, 9,
                formatInterfaceInfo(now, status?.interfaceInfo),
            )
        }

        drawProgressBar(status?.timestamp, now, status?.cycleLength)
    }
}