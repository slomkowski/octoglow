package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.fasterxml.jackson.core.type.TypeReference
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
import kotlin.coroutines.coroutineContext

//todo ping gateway, ping dns, show ip of current interface, show wifi strength when is wifi
//

class NetworkView(
        private val hardware: Hardware)
    : FrontDisplayView("Network", Duration.ofSeconds(30), Duration.ofSeconds(15)) {

    data class RouteEntry(
            val dst: String,
            val gateway : String?,
            val dev : String,
            val protocol : String,
            val metric : Int)

    data class InterfaceInfo(
            val name: String,
            val ip: Inet4Address,
            val isWifi : Boolean)

    data class WifiSignalInfo(
            val ifName: String,
            val linkQuality: Double,
            val signalStrength: Double,
            val noiseLevel: Double)

    private var activeInterface: InterfaceInfo? = null

    private var currentWifiSignalInfo : WifiSignalInfo? = null

    companion object : KLogging() {

        private val PROC_NET_WIRELESS_PATH : Path = Paths.get("/proc/net/wireless")
        private val IP_BINARY_PATH : Path = Paths.get("/usr/bin/ip")
        private val ipGetRoutesProcessBuilder = ProcessBuilder(IP_BINARY_PATH.toString(), "-json", "-pretty","route" )

        fun getRouteEntries() : List<RouteEntry> {
            val process = ipGetRoutesProcessBuilder.start()
            val output = IOUtils.toString(process.inputStream, StandardCharsets.UTF_8)

            process.waitFor(5, TimeUnit.SECONDS)

            return jacksonObjectMapper.readValue(output, object : TypeReference<List<RouteEntry>>() {})
        }

        /**
         * We assume that only one interface is active and is routing all traffic.
         */
        private fun getDefaultRouteEntry(entries: Collection<RouteEntry>): RouteEntry? = entries.filter { it.dst == "default" }.minBy { it.metric }

        /**
         * We assume that the interface has only one IP.
         */
        fun getActiveInterfaceInfo(): InterfaceInfo? = getRouteEntries()
                .let { getDefaultRouteEntry(it) }
                ?.let { NetworkInterface.getByName(it.dev) }
                ?.let { InterfaceInfo(it.name, it.inetAddresses.toList().first { it is Inet4Address } as Inet4Address, !it.isEthernet()) }


        fun parseProcNetWirelessFile(reader: BufferedReader): List<WifiSignalInfo> = reader.lines().skip(2).map { line ->
            val columns = line.split(Regex("\\s+"))

            WifiSignalInfo(columns[0].trim(':'),
                    columns[2].toDouble(),
                    columns[3].toDouble(),
                    columns[4].toDouble())
        }.collect(Collectors.toList())

        private fun NetworkInterface.isEthernet(): Boolean = listOf("eth", "enp").any { this.name.startsWith(it) }
    }

    override suspend fun poolStatusData(): UpdateStatus {
        return try {
            activeInterface = checkNotNull(getActiveInterfaceInfo()) { "cannot determine currently active network interface" }
            UpdateStatus.FULL_SUCCESS
        } catch (e: Exception) {
            activeInterface = null
            UpdateStatus.FAILURE
        }
    }

    override suspend fun poolInstantData(): UpdateStatus {
       currentWifiSignalInfo = if(activeInterface?.isWifi == true) {
           try {
               val wifiInterfaces = parseProcNetWirelessFile(Files.newBufferedReader(PROC_NET_WIRELESS_PATH))

           }catch (e : Exception) {
               logger.error { "Cannot determine " }
               null
           }
       } else {
           null
       }
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
       val fd = hardware.frontDisplay
        val ai = activeInterface

        if(redrawStatic) {
          launch {   fd.setStaticText(0, "IP:") }
        }

        if(redrawStatus) {
            val text = ai?.ip?.hostAddress ?: "---.---.---.---"
            launch { fd.setStaticText(3, text) }
        }


        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}