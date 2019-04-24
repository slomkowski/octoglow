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

    companion object : KLogging()

    private val operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)

    private var previousDacValue = -1

    override suspend fun pool() {
        val newValue = (operatingSystemMXBean.systemCpuLoad * 255.0).roundToInt()

        if (newValue != previousDacValue) {
            hardware.dac.setValue(DacChannel.C2, newValue)
            previousDacValue = newValue
        }
    }
}