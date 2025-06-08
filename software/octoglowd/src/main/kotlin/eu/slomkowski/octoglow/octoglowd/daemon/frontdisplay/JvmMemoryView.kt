package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import com.sun.management.OperatingSystemMXBean
import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.now
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean
import kotlin.math.min
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class JvmMemoryView(
    hardware: Hardware,
) : FrontDisplayView(
    hardware,
    "JVM Memory",
    10.minutes,
    1.seconds, //todo
    5.seconds,
) {
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

    @Volatile
    private var currentReport: CurrentReport? = null

    private fun updateJvmStatus(): UpdateStatus {
        val (status, newReport) = try {
            val runtime = Runtime.getRuntime()

            UpdateStatus.FULL_SUCCESS to CurrentReport(
                now(),
                runtime.totalMemory(),
                runtime.freeMemory(),
                noThreads = threadBean.threadCount,
                cpuLoad = operatingSystemMXBean.cpuLoad,
            )
        } catch (e: Exception) {
            UpdateStatus.FAILURE to null
        }

        currentReport = newReport

        return status
    }

    override suspend fun poolStatusData(now: Instant): UpdateStatus {
        return updateJvmStatus()
    }

    override suspend fun poolInstantData(now: Instant): UpdateStatus {
        return updateJvmStatus()
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val report = currentReport
            val fd = hardware.frontDisplay

            if (redrawStatus) {
                fd.clear()
                fd.setStaticText(0, "CPU:")
                fd.setStaticText(20, "mem")
                fd.setStaticText(7, "% threads:")
                fd.setStaticText(27, "MB free")
                fd.setStaticText(38, "MB")
            }

            launch {
                fd.setStaticText(5, report?.cpuLoad?.let { String.format("%2.0f", min(100.0 * it, 99.0)) } ?: "--")
                fd.setStaticText(18, report?.noThreads?.let { String.format("%2d", it) } ?: "--")
                fd.setStaticText(24, report?.totalMemory?.let { String.format("%2.0f", it / 1024.0 / 1024.0) } ?: "--")
                fd.setStaticText(35, report?.freeMemory?.let { String.format("%2.0f", it / 1024.0 / 1024.0) } ?: "--")
            }

            Unit
        }
}