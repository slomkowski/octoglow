package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.fasterxml.jackson.core.type.TypeReference
import eu.slomkowski.octoglow.octoglowd.jacksonObjectMapper
import mu.KLogging
import org.apache.commons.io.IOUtils
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.TimeUnit

//todo ping gateway, ping dns, show ip of current interface, show wifi strength when is wifi
//

class NetworkView : FrontDisplayView("Network", Duration.ofSeconds(30), Duration.ofSeconds(15)) {

    data class RouteEntry(
            val dst: String,
            val gateway : String?,
            val dev : String,
            val protocol : String,
            val metric : Int)

    companion object : KLogging() {

        private val IP_BINARY_PATH : Path = Paths.get("/usr/bin/ip")
        private val ipGetRoutesProcessBuilder = ProcessBuilder(IP_BINARY_PATH.toString(), "-json", "-pretty","route" )

        fun getInterfaces() {
           val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            logger.debug { "Found ${interfaces.size} interfaces."}

        }

        fun getRouteEntries() : List<RouteEntry> {
            val process = ipGetRoutesProcessBuilder.start()
            val output = IOUtils.toString(process.inputStream, StandardCharsets.UTF_8)

            process.waitFor(5, TimeUnit.SECONDS)

            return jacksonObjectMapper.readValue(output, object : TypeReference<List<RouteEntry>>() {})
        }

        fun getDefaultRouteEntry(entries : Collection<RouteEntry>) = entries.filter { it.gateway == "default" }.minBy { it.metric }
    }

    // display current ip of interface, which routes default gw

    override suspend fun poolStatusData(): UpdateStatus {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun poolInstantData(): UpdateStatus {
        // if wifi, draw wifi strength

        TODO()
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}