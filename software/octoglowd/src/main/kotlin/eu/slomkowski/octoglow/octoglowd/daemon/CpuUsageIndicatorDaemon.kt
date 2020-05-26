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
        private val hardware: Hardware)
    : Daemon(config, hardware, logger, Duration.ofMillis(500)) {

    companion object : KLogging() {
        const val CORRECTION_FACTOR: Double = 0.95
    }

    private val operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)

    override suspend fun pool() {
        setValue(operatingSystemMXBean.systemCpuLoad)
    }

    suspend fun setValue(v: Double) = hardware.dac.setValue(DacChannel.C2, (v * 255.0 * CORRECTION_FACTOR).roundToInt())
}