package eu.slomkowski.octoglow.octoglowd.controller

import com.sun.management.OperatingSystemMXBean
import eu.slomkowski.octoglow.octoglowd.hardware.DacChannel
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import java.lang.management.ManagementFactory
import java.time.Duration
import kotlin.math.roundToInt


class CpuUsageIndicatorController(
        private val hardware: Hardware)
    : Controller(Duration.ofMillis(500)) {
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