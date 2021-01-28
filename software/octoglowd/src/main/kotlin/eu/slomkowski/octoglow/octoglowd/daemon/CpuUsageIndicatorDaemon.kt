package eu.slomkowski.octoglow.octoglowd.daemon

import com.sun.management.OperatingSystemMXBean
import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.hardware.DacChannel
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import mu.KLogging
import java.lang.management.ManagementFactory
import java.time.Duration
import kotlin.math.roundToInt


class CpuUsageIndicatorDaemon(
    config: Config,
    private val hardware: Hardware
) : Daemon(config, hardware, logger, Duration.ofMillis(500)) {

    companion object : KLogging() {
        private val CPU_CHANNEL = DacChannel.C2
        private val MEM_CHANNEL = DacChannel.C1
    }

    private val operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)

    override suspend fun pool() {
        setValue(MEM_CHANNEL, operatingSystemMXBean.let {
            (it.totalPhysicalMemorySize - it.freePhysicalMemorySize).toDouble() / it.totalPhysicalMemorySize
        })

        setValue(CPU_CHANNEL, operatingSystemMXBean.systemCpuLoad)
    }

    suspend fun setValue(channel: DacChannel, v: Double) = hardware.dac.setValue(channel, (v * 255.0).roundToInt())
}