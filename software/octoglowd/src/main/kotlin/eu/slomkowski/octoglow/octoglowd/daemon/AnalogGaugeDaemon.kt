package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.MANY_WHITESPACES_REGEX
import eu.slomkowski.octoglow.octoglowd.hardware.DacChannel
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds


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

    fun parseProcStatFile(reader: BufferedReader): Double {
        val stat = reader.readLine()
            .split(MANY_WHITESPACES_REGEX)
            .drop(1) // drop "cpu" prefix
            .map { it.toLong() }

        val idle = stat[3]
        val total = stat.sum()

        val deltaIdle = idle - cpuLastIdle
        val deltaTotal = total - cpuLastTotal

        if (deltaIdle == deltaTotal) {
            return 0.0
        }

        cpuLastIdle = idle
        cpuLastTotal = total

        return 1.0 - (deltaIdle.toDouble() / deltaTotal.toDouble())
    }

    private var cpuLastIdle: Long = 0
    private var cpuLastTotal: Long = 0

    override suspend fun poll() = coroutineScope {
        launch {
            setValue(CPU_CHANNEL, getCpuLoad())
        }

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

    private suspend fun getCpuLoad(): Double = withContext(Dispatchers.IO) {
        Files.newBufferedReader(Paths.get("/proc/stat")).use { parseProcStatFile(it) }
    }

    private suspend fun gatherWiFiInterfaces(): List<WifiSignalInfo> = withContext(Dispatchers.IO) {
        Files.newBufferedReader(PROC_NET_WIRELESS_PATH).use { parseProcNetWirelessFile(it) }
    }

    private class BufferState {
        val buffer: DoubleArray = DoubleArray(NUMBER_OF_SAMPLES_TO_AVERAGE) { 0.5 }
        var currentIndex: Int = 0
    }

    private val valueHistory = DacChannel.entries.associateWith { BufferState() }

    private suspend fun setValue(channel: DacChannel, v: Double) {
        val state = valueHistory.getValue(channel)
        val buffer = state.buffer

        buffer[state.currentIndex] = v
        state.currentIndex = (state.currentIndex + 1) % NUMBER_OF_SAMPLES_TO_AVERAGE

        var sum = 0.0
        for (i in 0 until NUMBER_OF_SAMPLES_TO_AVERAGE) {
            sum += buffer[i]
        }
        val averagedValue = sum / NUMBER_OF_SAMPLES_TO_AVERAGE

        hardware.dac.setValue(channel, (averagedValue * 255.0).roundToInt())
    }
}