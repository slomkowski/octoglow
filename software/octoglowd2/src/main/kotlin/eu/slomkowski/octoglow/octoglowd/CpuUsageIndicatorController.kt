package eu.slomkowski.octoglow.octoglowd

import com.sun.management.OperatingSystemMXBean
import eu.slomkowski.octoglow.octoglowd.hardware.Dac
import eu.slomkowski.octoglow.octoglowd.hardware.DacChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import java.lang.management.ManagementFactory
import kotlin.math.roundToInt


fun CoroutineScope.createCpuUsageIndicatorController(dac: Dac) = launch {
    val operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)

    var previousDacValue = -1

    for (t in ticker(500)) {
        val newValue = (operatingSystemMXBean.systemCpuLoad * 255.0).roundToInt()

        if (newValue != previousDacValue) {
            dac.setValue(DacChannel.C2, newValue)
            previousDacValue = newValue
        }
    }
}