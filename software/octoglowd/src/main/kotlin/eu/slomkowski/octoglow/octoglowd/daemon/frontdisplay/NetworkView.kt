package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.NetworkViewKey
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.readToString
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KLogging
import java.io.BufferedReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.math.roundToLong

class NetworkView(
        private val config: Config,
        hardware: Hardware)
    : FrontDisplayView(hardware,
        "Network",
        Duration.ofSeconds(44),
        Duration.ofSeconds(2),
        Duration.ofSeconds(5)) {

    data class RouteEntry(
            val dst: InetAddress,
            val gateway: InetAddress,
            val dev: String,
            val metric: Int)

    data class InterfaceInfo(
            val name: String,
            val ip: Inet4Address,
            val isWifi: Boolean)

    data class WifiSignalInfo(
            val ifName: String,
            val linkQuality: Double, // in percent
            val signalStrength: Double, // in dBm
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

    data class CurrentReport(
            val interfaceInfo: InterfaceInfo,
            val currentPing: Duration?)

    private var currentReport: CurrentReport? = null

    private var currentWifiSignalInfo: WifiSignalInfo? = null

    companion object : KLogging() {

        private val PROC_NET_WIRELESS_PATH: Path = Paths.get("/proc/net/wireless")
        private val PROC_NET_ROUTE_PATH: Path = Paths.get("/proc/net/route")

        /**
         * We assume that the interface has only one IP.
         */
        fun getActiveInterfaceInfo(): InterfaceInfo? = Files.newBufferedReader(PROC_NET_ROUTE_PATH).use { reader ->
            getDefaultRouteEntry(parseProcNetRouteFile(reader))
                    ?.let { NetworkInterface.getByName(it.dev) }
                    ?.let { iface ->
                        InterfaceInfo(iface.name,
                                iface.inetAddresses.toList().first { it is Inet4Address } as Inet4Address,
                                !iface.isEthernet())
                    }
        }

        /**
         * We assume that only one interface is active and is routing all traffic.
         */
        private fun getDefaultRouteEntry(entries: Collection<RouteEntry>): RouteEntry? = entries.filter { it.dst == InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0)) }.minBy { it.metric }

        fun createIpFromHexString(str: String): InetAddress {
            require(str.length == 8) { "the string has to be 8 hex digits" }
            return InetAddress.getByName(str.chunked(2).asReversed().joinToString(".") { it.toInt(16).toString() })
        }

        fun parseProcNetRouteFile(reader: BufferedReader): List<RouteEntry> = reader.lines().skip(1).map { line ->
            val columns = line.trim().split(Regex("\\s+"))

            RouteEntry(
                    createIpFromHexString(columns[1].trim()),
                    createIpFromHexString(columns[2].trim()),
                    columns[0].trim(),
                    columns[6].trim().toInt())
        }.collect(Collectors.toList())


        fun parseProcNetWirelessFile(reader: BufferedReader): List<WifiSignalInfo> = reader.lines().skip(2).map { line ->
            val columns = line.trim().split(Regex("\\s+"))

            WifiSignalInfo(columns[0].trim(':'),
                    columns[2].toDouble() / 70.0 * 100.0,
                    columns[3].toDouble(),
                    columns[4].toDouble())
        }.collect(Collectors.toList())

        fun pingAddress(pingBinary: Path, iface: String, address: String, timeout: Duration, noPings: Int): PingResult {
            require(iface.isNotBlank())
            require(address.isNotBlank())
            require(noPings > 0)
            require(!timeout.isNegative)
            val pb = ProcessBuilder(pingBinary.toAbsolutePath().toString(),
                    "-4",
                    "-c", noPings.toString(),
                    "-i", "0.2",
                    "-I", iface,
                    "-w", timeout.seconds.toString(),
                    address)

            val process = pb.start()
            val output = StringBuilder()

            while (process.isAlive) {
                output.append(process.inputStream.readToString())
            }

            process.waitFor(timeout.seconds + 2, TimeUnit.SECONDS)

            check(output.isNotBlank()) {
                val errorMsg = process.errorStream.readToString()
                "ping returned no output, error is: $errorMsg"
            }

            return parsePingOutput(output.toString())
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

        fun formatPingRtt(d: Duration?): String = when (val ms = d?.toMillis()?.toInt()) {
            null -> " -- ms"
            0 -> " <1 ms"
            in 1..99 -> String.format(" %2d ms", ms)
            in 100..999 -> String.format(" %3dms", ms)
            else -> ">999ms"
        }

        private fun NetworkInterface.isEthernet(): Boolean = listOf("eth", "enp").any { this.name.startsWith(it) }
    }

    override suspend fun poolStatusData(): UpdateStatus {
        val (newReport, updateStatus) = try {
            val iface = checkNotNull(getActiveInterfaceInfo()) { "cannot determine currently active network interface" }

            try {
                val address = config[NetworkViewKey.pingAddress]
                logger.debug {
                    "Pinging $address via interface ${iface.name} (" + when (iface.isWifi) {
                        true -> "wireless"
                        false -> "wired"
                    } + ")."
                }

                val pingInfo = pingAddress(config[NetworkViewKey.pingBinary], iface.name, address, Duration.ofSeconds(4), 3)

                val pingTime = pingInfo.rttAvg

                logger.info { "RTT to $address is ${pingTime.toMillis()} ms." }

                CurrentReport(iface, pingTime) to UpdateStatus.FULL_SUCCESS
            } catch (e: Exception) {
                logger.error(e) { "Error when ping." }
                CurrentReport(iface, null) to UpdateStatus.PARTIAL_SUCCESS
            }
        } catch (e: Exception) {
            logger.error(e) { "Cannot determine active network interface." }
            null to UpdateStatus.FAILURE
        }

        currentReport = newReport

        return updateStatus
    }

    override suspend fun poolInstantData(): UpdateStatus {
        val ai = currentReport?.interfaceInfo
        val (newReport, updateStatus) = if (ai?.isWifi == true) {
            try {
                val wifiInterfaces = Files.newBufferedReader(PROC_NET_WIRELESS_PATH).use { parseProcNetWirelessFile(it) } //todo use non-blocking api
                val activeInfo = checkNotNull(wifiInterfaces.firstOrNull { it.ifName == ai.name })
                { "cannot find interface $ai in $PROC_NET_WIRELESS_PATH" }

                logger.debug { "Wi-Fi signal strength on ${activeInfo.ifName} is ${activeInfo.linkQuality}%." }

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
        val cr = currentReport
        val wifiInfo = currentWifiSignalInfo

        if (redrawStatic) {
            launch { fd.setStaticText(0, "IP:") }
            launch { fd.setStaticText(29, "ping") }
        }

        if (redrawStatus) {

            val text = cr?.interfaceInfo?.ip?.hostAddress ?: "---.---.---.---"
            launch { fd.setStaticText(4, text) }

            if (cr != null) {
                launch {
                    fd.setStaticText(20,
                            when (cr.interfaceInfo.isWifi) {
                                false -> "wired"
                                true -> "wifi"
                            })
                }

                launch {
                    fd.setStaticText(34, formatPingRtt(cr.currentPing))
                }
            }
        }

        if (cr?.interfaceInfo?.isWifi == true && wifiInfo != null) {
            launch {
                fd.setStaticText(25, String.format("%2.0f%%", wifiInfo.linkQuality))
            }
        }
    }

}