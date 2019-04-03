package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.fasterxml.jackson.core.type.TypeReference
import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.NetworkViewKey
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.jacksonObjectMapper
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KLogging
import org.apache.commons.io.IOUtils
import java.io.BufferedReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.math.roundToLong

//todo ping gateway, ping dns, show ip of current interface, show wifi strength when is wifi
//

class NetworkView(
        private val config: Config,
        private val hardware: Hardware)
    : FrontDisplayView("Network", Duration.ofSeconds(30), Duration.ofSeconds(15)) {

    data class RouteEntry(
            val dst: String,
            val gateway: String?,
            val dev: String,
            val protocol: String?,
            val metric: Int)

    data class InterfaceInfo(
            val name: String,
            val ip: Inet4Address,
            val isWifi: Boolean)

    data class WifiSignalInfo(
            val ifName: String,
            val linkQuality: Double,
            val signalStrength: Double,
            val noiseLevel: Double)

    data class PingResult(
            val packetsTransmitted: Int,
            val packetsReceived: Int,
            val rttMin: Duration,
            val rttAvg: Duration,
            val rttMax: Duration) {
        init {
            require(packetsReceived >= 0)
            require(packetsTransmitted > 0)
            require(packetsReceived <= packetsTransmitted)
            require(rttMin <= rttAvg)
            require(rttAvg <= rttMax)
        }
    }

    private var activeInterface: InterfaceInfo? = null

    private var currentWifiSignalInfo: WifiSignalInfo? = null

    companion object : KLogging() {

        private val PROC_NET_WIRELESS_PATH: Path = Paths.get("/proc/net/wireless")

        fun getRouteEntries(ipBinary: Path): List<RouteEntry> {
            val pb = ProcessBuilder(ipBinary.toAbsolutePath().toString(), "-json", "-pretty", "route")
            val process = pb.start() //todo maybe coroutine?
            val output = IOUtils.toString(process.inputStream, StandardCharsets.UTF_8)

            process.waitFor(5, TimeUnit.SECONDS)

            return jacksonObjectMapper.readValue(output, object : TypeReference<List<RouteEntry>>() {})
        }

        /**
         * We assume that only one interface is active and is routing all traffic.
         */
        private fun getDefaultRouteEntry(entries: Collection<RouteEntry>): RouteEntry? = entries.filter { it.dst == "default" }.minBy { it.metric }




        fun parseProcNetWirelessFile(reader: BufferedReader): List<WifiSignalInfo> = reader.lines().skip(2).map { line ->
            val columns = line.trim().split(Regex("\\s+"))

            WifiSignalInfo(columns[0].trim(':'),
                    columns[2].toDouble(),
                    columns[3].toDouble(),
                    columns[4].toDouble())
        }.collect(Collectors.toList())

        fun pingAddress(pingBinary: Path, iface: String, address: String, timeout: Duration): PingResult {
            require(iface.isNotBlank())
            require(address.isNotBlank())
            require(!timeout.isNegative)
            val pb = ProcessBuilder(pingBinary.toAbsolutePath().toString(),
                    "-4",
                    "-c", "3",
                    "-i", "0.2",
                    "-I", iface,
                    "-D", timeout.seconds.toString())

            val process = pb.start()

            process.waitFor(timeout.seconds + 2, TimeUnit.SECONDS)
            val output = IOUtils.toString(process.inputStream, StandardCharsets.UTF_8)

            check(output.isNotBlank()) { "ping returned no output" }

            return parsePingOutput(output)
        }

        fun parsePingOutput(text: String): PingResult {
            val packetRegex = Regex("(\\d+) packets transmitted, (\\d+) received")
            val rttRegex = Regex("rtt min/avg/max/mdev = (\\d+\\.\\d+)/(\\d+\\.\\d+)/(\\d+\\.\\d+)/(\\d+\\.\\d+) ms")

            val (packetsTransmitted, packetsReceived) = checkNotNull(packetRegex.find(text))
            { "info about packet numbers not found in ping output" }.groupValues.let {
                it[1].toInt() to it[2].toInt()
            }

            val (rttMin, rttAvg, rttMax) = checkNotNull(rttRegex.find(text))
            { "info about RTT not found in ping output" }
                    .groupValues
                    .subList(1, 4)
                    .map { Duration.ofNanos((it.toDouble() * 1_000_000.0).roundToLong()) }

            return PingResult(packetsTransmitted, packetsReceived, rttMin, rttAvg, rttMax)
        }

        private fun NetworkInterface.isEthernet(): Boolean = listOf("eth", "enp").any { this.name.startsWith(it) }
    }

    override suspend fun poolStatusData(): UpdateStatus {
        return try {
            activeInterface = checkNotNull(getActiveInterfaceInfo()) { "cannot determine currently active network interface" }
            UpdateStatus.FULL_SUCCESS
        } catch (e: Exception) {
            activeInterface = null
            logger.error(e) { "Cannot determine active network interface." }
            UpdateStatus.FAILURE
        }
    }

    override suspend fun poolInstantData(): UpdateStatus {
        val ai = activeInterface
        val (newReport, updateStatus) = if (ai?.isWifi == true) {
            try {
                val wifiInterfaces = parseProcNetWirelessFile(Files.newBufferedReader(PROC_NET_WIRELESS_PATH))
                val activeInfo = checkNotNull(wifiInterfaces.firstOrNull { it.ifName == ai.name })
                { "cannot find interface $ai in $PROC_NET_WIRELESS_PATH" }

                activeInfo to UpdateStatus.FULL_SUCCESS
            } catch (e: Exception) {
                logger.error(e) { "Cannot determine Wi-Fi signal level" }
                null to UpdateStatus.FAILURE
            }
        } else {
            null to UpdateStatus.NO_NEW_DATA
        }

        currentWifiSignalInfo = newReport
        return updateStatus
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
        val fd = hardware.frontDisplay
        val ai = activeInterface

        if (redrawStatic) {
            launch { fd.setStaticText(0, "IP:") }
        }

        if (redrawStatus) {
            val text = ai?.ip?.hostAddress ?: "---.---.---.---"
            launch { fd.setStaticText(4, text) }

            if (ai?.isWifi == false) {
                launch { fd.setStaticText(20, "ethernet") }
            }
        }
    }

    /**
     * We assume that the interface has only one IP.
     */
    fun getActiveInterfaceInfo(): InterfaceInfo? = getRouteEntries(config[NetworkViewKey.ipBinary])
            .let { getDefaultRouteEntry(it) }
            ?.let { NetworkInterface.getByName(it.dev) }
            ?.let { InterfaceInfo(it.name, it.inetAddresses.toList().first { it is Inet4Address } as Inet4Address, !it.isEthernet()) }
}