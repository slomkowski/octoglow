@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.DataSnapshot
import eu.slomkowski.octoglow.octoglowd.PingTimeGateway
import eu.slomkowski.octoglow.octoglowd.PingTimeRemoteHost
import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.dataharvesters.NetworkDataHarvester
import eu.slomkowski.octoglow.octoglowd.dataharvesters.NetworkDataSnapshot
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
        val interfaceInfo: NetworkDataHarvester.InterfaceInfo?,
        val remotePing: Duration?,
        val gwPing: Duration?,
    )

    companion object {
        private val logger = KotlinLogging.logger {}

        fun formatPingRtt(d: Duration?): String = when (val ms = d?.inWholeMilliseconds) {
            null -> " -- ms"
            0L -> " <1 ms"
            in 1..99 -> String.format(" %2d ms", ms)
            in 100..999 -> String.format(" %3dms", ms)
            else -> ">999ms"
        }

    }

    override suspend fun onNewDataSnapshot(
        snapshot: Snapshot,
        oldStatus: CurrentReport?
    ): UpdateStatus {

        if (snapshot !is DataSnapshot) {
            return UpdateStatus.NoNewData
        }

        if (snapshot is NetworkDataSnapshot && snapshot.interfaceInfo == null) {
            return UpdateStatus.NewData(null)
        }

        val newRemotePing = when (val v = snapshot.values.firstOrNull { it.type == PingTimeRemoteHost }?.value) {
            null -> oldStatus?.remotePing
            else -> v.getOrNull()?.milliseconds
        }

        val newGwPing = when (val v = snapshot.values.firstOrNull { it.type == PingTimeGateway }?.value) {
            null -> oldStatus?.gwPing
            else -> v.getOrNull()?.milliseconds
        }

        val interfaceInfo = (snapshot as? NetworkDataSnapshot)?.interfaceInfo

        if (newRemotePing != null && newGwPing != null && interfaceInfo == null) {
            return UpdateStatus.NoNewData
        }

        return UpdateStatus.NewData(
            CurrentReport(
                snapshot.timestamp,
                snapshot.cycleLength ?: oldStatus?.cycleLength ?: 5.minutes,
                interfaceInfo ?: oldStatus?.interfaceInfo,
                newRemotePing ?: oldStatus?.remotePing,
                newGwPing ?: oldStatus?.gwPing,
            )
        )
    }

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: CurrentReport?,
        instant: Unit?
    ): Unit = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            launch { fd.setStaticText(0, "IP:") }
            launch { fd.setStaticText(20, "ping") }
            launch { fd.setStaticText(32, "gw") }
        }

        if (redrawStatus) {

            val text = status?.interfaceInfo?.ip?.hostAddress ?: "---.---.---.---"
            launch { fd.setStaticText(4, text) }

            if (status != null) {
                launch {
                    fd.setStaticText(
                        16,
                        when (status.interfaceInfo?.isWifi) {
                            false -> "wire"
                            true -> "wifi"
                            else -> "----"
                        }
                    )
                }

                launch {
                    fd.setStaticText(24, formatPingRtt(status.remotePing))
                    fd.setStaticText(34, formatPingRtt(status.gwPing))
                }
            }
        }

        drawProgressBar(status?.timestamp, now, status?.cycleLength)
    }
}