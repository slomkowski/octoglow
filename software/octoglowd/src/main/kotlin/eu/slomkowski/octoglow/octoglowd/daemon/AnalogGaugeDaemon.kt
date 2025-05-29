package eu.slomkowski.octoglow.octoglowd.daemon

import com.sun.management.OperatingSystemMXBean
import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.hardware.DacChannel
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds


class AnalogGaugeDaemon(
    config: Config,
    private val hardware: Hardware
) : Daemon(config, hardware, logger, 700.milliseconds) {

    data class WifiSignalInfo(
        val ifName: String,
        val linkQuality: Double, // in percent
        val signalStrength: Double, // in dBm
        val noiseLevel: Double
    )

    companion object {
        private val logger = KotlinLogging.logger {}
        private val CPU_CHANNEL = DacChannel.C2
        private val WIFI_CHANNEL = DacChannel.C1

        private val PROC_NET_WIRELESS_PATH: Path = Paths.get("/proc/net/wireless")

        fun parseProcNetWirelessFile(reader: BufferedReader): List<WifiSignalInfo> =
            reader.lines().skip(2).map { line ->
                val columns = line.trim().split(Regex("\\s+"))

                WifiSignalInfo(
                    columns[0].trim(':'),
                    columns[2].toDouble() / 70.0 * 100.0,
                    columns[3].toDouble(),
                    columns[4].toDouble()
                )
            }.collect(Collectors.toList())
    }

    private val operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)

    override suspend fun pool() = coroutineScope {
        launch { setValue(CPU_CHANNEL, operatingSystemMXBean.cpuLoad) }

        val wifiInterfaces = withContext(Dispatchers.IO) {
            Files.newBufferedReader(PROC_NET_WIRELESS_PATH).use { parseProcNetWirelessFile(it) }
        }

        check(wifiInterfaces.size <= 1) { "more than one active Wi-Fi interface found" }

        val interfaceInfo = wifiInterfaces.singleOrNull()

        launch {
            setValue(
                WIFI_CHANNEL, if (interfaceInfo != null) {
                    interfaceInfo.linkQuality / 100.0
                } else {
                    0.0
                }
            )
        }

        Unit
    }

    private suspend fun setValue(channel: DacChannel, v: Double) =
        hardware.dac.setValue(channel, (v * 255.0).roundToInt())
}