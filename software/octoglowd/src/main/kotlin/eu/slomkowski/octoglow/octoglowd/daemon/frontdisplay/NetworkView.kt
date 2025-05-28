package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.readToString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import java.io.BufferedReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds


class NetworkView(
    private val config: Config,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "Network",
    38.seconds,
    37.seconds,
    5.seconds
) {

    data class RouteEntry(
        val dst: InetAddress,
        val gateway: InetAddress,
        val dev: String,
        val metric: Int
    )

    data class InterfaceInfo(
        val name: String,
        val ip: Inet4Address,
        val gatewayIp: Inet4Address,
        val isWifi: Boolean
    )

    data class PingResult(
        val packetsTransmitted: Int,
        val packetsReceived: Int,
        val rttMin: Duration,
        val rttAvg: Duration,
        val rttMax: Duration
    ) {
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
        val remotePing: Duration?,
        val gwPing: Duration?
    )

    private var currentReport: CurrentReport? = null

    companion object {
        private val logger = KotlinLogging.logger {}

        private val PROC_NET_WIRELESS_PATH: Path = Paths.get("/proc/net/wireless")
        private val PROC_NET_ROUTE_PATH: Path = Paths.get("/proc/net/route")

        /**
         * We assume that the interface has only one IP.
         */
        fun getActiveInterfaceInfo(): InterfaceInfo? = Files.newBufferedReader(PROC_NET_ROUTE_PATH).use { reader ->
            getDefaultRouteEntry(parseProcNetRouteFile(reader))
                ?.let { it to NetworkInterface.getByName(it.dev) }
                ?.let { (re, iface) ->
                    InterfaceInfo(
                        iface.name,
                        iface.inetAddresses.toList().first { it is Inet4Address } as Inet4Address,
                        re.gateway as Inet4Address,
                        !iface.isEthernet())
                }
        }

        /*
         * We assume that only one interface is active and is routing all traffic.
         */
        private fun getDefaultRouteEntry(entries: Collection<RouteEntry>): RouteEntry? =
            entries.filter { it.dst == InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0)) }.minByOrNull { it.metric }

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
                columns[6].trim().toInt()
            )
        }.collect(Collectors.toList())

        fun pingAddressAndGetRtt(
            pingBinary: Path,
            iface: String,
            address: String,
            timeout: Duration,
            noPings: Int
        ): PingResult {
            require(iface.isNotBlank())
            require(address.isNotBlank())
            require(noPings > 0)
            require(timeout > Duration.ZERO)
            val pb = ProcessBuilder(
                pingBinary.toAbsolutePath().toString(),
                "-4",
                "-c", noPings.toString(),
                "-i", "0.2",
                "-I", iface,
                "-w", timeout.inWholeSeconds.toString(),
                address
            )

            val process = pb.start()
            val output = StringBuilder()

            while (process.isAlive) {
                output.append(process.inputStream.readToString())
            }

            process.waitFor(timeout.inWholeMilliseconds + 2000, TimeUnit.MILLISECONDS)

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
                .map { (it.toDouble() * 1_000_000.0).roundToLong().nanoseconds }

            return PingResult(packetsTransmitted, packetsReceived, rttMin, rttAvg, rttMax)
        }

        fun formatPingRtt(d: Duration?): String = when (val ms = d?.inWholeMilliseconds) {
            null -> " -- ms"
            0L -> " <1 ms"
            in 1..99 -> String.format(" %2d ms", ms)
            in 100..999 -> String.format(" %3dms", ms)
            else -> ">999ms"
        }

        private fun NetworkInterface.isEthernet(): Boolean = listOf("eth", "enp").any { this.name.startsWith(it) }
    }

    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {
        val (newReport, updateStatus) = try {
            val iface =
                checkNotNull(withContext(Dispatchers.IO) { getActiveInterfaceInfo() }) { "cannot determine currently active network interface" }

            val gatewayPingTime = async {
                try {
                    pingAddressAndGetRtt(iface.gatewayIp.hostAddress, iface)
                } catch (e: Exception) {
                    logger.error(e) { "Error during gateway ping." }
                    null
                }
            }

            val remotePingTime = async {
                try {
                    val remoteAddress = config.networkInfo.pingAddress
                    pingAddressAndGetRtt(remoteAddress, iface)
                } catch (e: Exception) {
                    logger.error(e) { "Error during remote ping ping." }
                    null
                }
            }

            val updateStatus = if (remotePingTime.await() != null && gatewayPingTime.await() != null) {
                UpdateStatus.FULL_SUCCESS
            } else {
                UpdateStatus.PARTIAL_SUCCESS
            }

            CurrentReport(iface, remotePingTime.await(), gatewayPingTime.await()) to updateStatus

        } catch (e: Exception) {
            logger.error(e) { "Cannot determine active network interface." }
            null to UpdateStatus.FAILURE
        }

        currentReport = newReport

        updateStatus
    }

    private suspend fun pingAddressAndGetRtt(
        address: String,
        interfaceInfo: InterfaceInfo
    ): Duration {

        logger.debug {
            "Pinging $address via interface ${interfaceInfo.name} (" + when (interfaceInfo.isWifi) {
                true -> "wireless"
                false -> "wired"
            } + ")."
        }

        val pingInfo = withContext(Dispatchers.IO) {
            pingAddressAndGetRtt(
                config.networkInfo.pingBinary,
                interfaceInfo.name,
                address,
                4.seconds,
                3
            )
        }

        val pingTime = pingInfo.rttAvg

        logger.info { "RTT to $address is ${pingTime.inWholeMilliseconds} ms." }

        return pingTime
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val fd = hardware.frontDisplay
            val cr = currentReport

            if (redrawStatic) {
                launch { fd.setStaticText(0, "IP:") }
                launch { fd.setStaticText(20, "ping") }
                launch { fd.setStaticText(32, "gw") }
            }

            if (redrawStatus) {

                val text = cr?.interfaceInfo?.ip?.hostAddress ?: "---.---.---.---"
                launch { fd.setStaticText(4, text) }

                if (cr != null) {
                    launch {
                        fd.setStaticText(
                            16,
                            when (cr.interfaceInfo.isWifi) {
                                false -> "wire"
                                true -> "wifi"
                            }
                        )
                    }

                    launch {
                        fd.setStaticText(24, formatPingRtt(cr.remotePing))
                        fd.setStaticText(34, formatPingRtt(cr.gwPing))
                    }
                }
            }
        }
}