package eu.slomkowski.octoglow.octoglowd.daemon

import com.sun.management.OperatingSystemMXBean
import eu.slomkowski.octoglow.octoglowd.MANY_WHITESPACES_REGEX
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
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class AnalogGaugeDaemon(
    private val hardware: Hardware,
) : Daemon(logger, 200.milliseconds) {

    data class WifiSignalInfo(
        val ifName: String,
        val linkQuality: Double, // in percent
        val signalStrength: Double, // in dBm
        val noiseLevel: Double,
    )

    companion object {
        private val logger = KotlinLogging.logger {}
        private val CPU_CHANNEL = DacChannel.C2
        private val WIFI_CHANNEL = DacChannel.C1

        private const val NUMBER_OF_SAMPLES_TO_AVERAGE = 5

        private val PROC_NET_WIRELESS_PATH: Path = Paths.get("/proc/net/wireless")

        fun parseProcNetWirelessFile(reader: BufferedReader): List<WifiSignalInfo> =
            reader.lines().skip(2).map { line ->
                val columns = line.trim().split(MANY_WHITESPACES_REGEX)

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

        val wifiInterfaces = gatherWiFiInterfaces()

        check(wifiInterfaces.size <= 1) { "more than one active Wi-Fi interface found" }

        val interfaceInfo = wifiInterfaces.singleOrNull()

        launch {
            setValue(
                WIFI_CHANNEL, if (interfaceInfo != null) {
                    interfaceInfo.linkQuality / 100.0
                } else {
                    0.5 // set to the middle of the gauge
                }
            )
        }

        Unit
    }

    private suspend fun gatherWiFiInterfaces(): List<WifiSignalInfo> = withContext(Dispatchers.IO) {
        Files.newBufferedReader(PROC_NET_WIRELESS_PATH).use { parseProcNetWirelessFile(it) }
    }

    private val valueHistory = DacChannel.entries.associateWith { ArrayDeque<Double>(NUMBER_OF_SAMPLES_TO_AVERAGE) }

    private suspend fun setValue(channel: DacChannel, v: Double) {

        val history = valueHistory.getValue(channel)

        if (history.size == NUMBER_OF_SAMPLES_TO_AVERAGE) {
            history.removeFirst()
        }
        history.addLast(v)

        val averagedValue = history.average()

        hardware.dac.setValue(channel, (averagedValue * 255.0).roundToInt())
    }
}