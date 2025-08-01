@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import com.sun.management.OperatingSystemMXBean
import eu.slomkowski.octoglow.octoglowd.datacollectors.MeasurementReport
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.now
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


class JvmMemoryView(
    hardware: Hardware,
) : FrontDisplayView2<Unit, JvmMemoryView.CurrentReport>(
    hardware,
    "JVM Memory",
    1.seconds,
    logger,
) {
    override val preferredDisplayTime: Duration = 5.seconds

    private val operatingSystemMXBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean::class.java)
    private val threadBean: ThreadMXBean = ManagementFactory.getThreadMXBean()

    data class CurrentReport(
        val timestamp: Instant,
        val totalMemory: Long,
        val freeMemory: Long,
        val noThreads: Int,
        val cpuLoad: Double,
    )

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun onNewMeasurementReport(report: MeasurementReport, oldStatus: Unit?) = UpdateStatus.NoNewData

    override suspend fun pollInstantData(now: Instant, oldInstant: CurrentReport?): UpdateStatus = UpdateStatus.NewData(
        try {
            val runtime = Runtime.getRuntime()

            CurrentReport(
                Clock.System.now(),
                runtime.totalMemory(),
                runtime.freeMemory(),
                noThreads = threadBean.threadCount,
                cpuLoad = operatingSystemMXBean.cpuLoad,
            )
        } catch (e: Exception) {
            null
        }
    )

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: Unit?,
        instant: CurrentReport?
    ): Unit = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            fd.clear()
            fd.setStaticText(0, "CPU:")
            fd.setStaticText(20, "mem")
            fd.setStaticText(7, "% threads:")
            fd.setStaticText(27, "MB free")
            fd.setStaticText(38, "MB")
        }

        launch {
            fd.setStaticText(5, instant?.cpuLoad?.let { String.format("%2.0f", min(100.0 * it, 99.0)) } ?: "--")
            fd.setStaticText(18, instant?.noThreads?.let { String.format("%2d", it) } ?: "--")
            fd.setStaticText(24, instant?.totalMemory?.let { String.format("%2.0f", it / 1024.0 / 1024.0) } ?: "--")
            fd.setStaticText(35, instant?.freeMemory?.let { String.format("%2.0f", it / 1024.0 / 1024.0) } ?: "--")
        }
    }
}