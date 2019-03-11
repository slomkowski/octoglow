package eu.slomkowski.octoglow.octoglowd.daemon.view

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.DatabaseLayer
import eu.slomkowski.octoglow.octoglowd.RadioactivityCpm
import eu.slomkowski.octoglow.octoglowd.RadioactivityUSVH
import eu.slomkowski.octoglow.octoglowd.hardware.GeigerDeviceState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime

class GeigerView(
        private val config: Config,
        private val database: DatabaseLayer,
        private val hardware: Hardware) : FrontDisplayView {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14

        private const val GEIGER_TUBE_SENSITIVITY = 25.0

        fun calculateCPM(v: Int, duration: Duration): Double = v.toDouble() / duration.toMinutes()

        fun calculateUSVh(v: Int, duration: Duration): Double = calculateCPM(v, duration) / 60.0 * 10.0 / GEIGER_TUBE_SENSITIVITY
    }

    data class CounterReport(
            val lastCPM: Double?, // counts-per-minute
            val lastUSVh: Double?, // uSv/h
            var progress: Duration,
            val timeSpan: Duration,
            val historical: List<Double?>?) {
        init {
            lastCPM?.let { it > 0 }
            lastUSVh?.let { it > 0 }
            require(progress <= timeSpan)
            historical?.let { require(it.size == HISTORIC_VALUES_LENGTH) }
        }
    }

    private var counterReport: CounterReport? = null

    private var deviceReport: GeigerDeviceState? = null

    override suspend fun redrawDisplay(firstTime: Boolean) = coroutineScope {
        // todo cpm, uSv/h, graph,ring when radioactivity > 0.03 uSv/h, geiger voltage, eye voltage, eye status
    }

    override suspend fun poolStateUpdate(): UpdateStatus {

        try {
            deviceReport = hardware.geiger.getDeviceState()
        } catch (e: Exception) {
            deviceReport = null
            logger.error(e) { "Cannot read device state." }
            return UpdateStatus.FAILURE
        }

        try {
            val cs = hardware.geiger.getCounterState()

            if (cs.hasNewCycleStarted || (cs.hasCycleEverCompleted && counterReport == null)) {
                val ts = LocalDateTime.now()
                val cpm = calculateCPM(cs.numOfCountsInPreviousCycle, cs.cycleLength)
                val usvh = calculateUSVh(cs.numOfCountsInPreviousCycle, cs.cycleLength)

                logger.info { "Read radioactivity: ${cs.numOfCountsInPreviousCycle}, $usvh uSv/h." }

                listOf(database.insertHistoricalValue(ts, RadioactivityCpm, cpm),
                        database.insertHistoricalValue(ts, RadioactivityUSVH, usvh)).joinAll()

                val historicalRadioactivity = database.getLastHistoricalValuesByHour(ts, RadioactivityUSVH, HISTORIC_VALUES_LENGTH)

                counterReport = CounterReport(cpm, usvh, cs.currentCycleProgress, cs.cycleLength, historicalRadioactivity.await())
            } else {
                val rep = counterReport
                if (rep == null) {
                    counterReport = CounterReport(null, null, cs.currentCycleProgress, cs.cycleLength, null)
                } else {
                    rep.progress = cs.currentCycleProgress
                }
            }
        } catch (e: Exception) {
            counterReport = null
            logger.error(e) { "Cannot read counter state." }
            return UpdateStatus.FAILURE
        }

        return UpdateStatus.FULL_SUCCESS
    }

    override fun getPreferredPoolingInterval(): Duration = Duration.ofSeconds(5)

    override val name: String
        get() = "Geiger Counter"
}