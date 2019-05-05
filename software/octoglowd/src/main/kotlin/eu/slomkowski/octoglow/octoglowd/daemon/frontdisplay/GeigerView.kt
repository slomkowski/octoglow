package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.DatabaseLayer
import eu.slomkowski.octoglow.octoglowd.RadioactivityCpm
import eu.slomkowski.octoglow.octoglowd.RadioactivityUSVH
import eu.slomkowski.octoglow.octoglowd.getSegmentNumber
import eu.slomkowski.octoglow.octoglowd.hardware.EyeInverterState
import eu.slomkowski.octoglow.octoglowd.hardware.GeigerDeviceState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime

class GeigerView(
        private val database: DatabaseLayer,
        hardware: Hardware)
    : FrontDisplayView(hardware,
        "Geiger counter",
        Duration.ofSeconds(7),
        Duration.ofSeconds(3),
        Duration.ofSeconds(24)) {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 4 * 5 - 1

        private const val GEIGER_TUBE_SENSITIVITY = 25.0

        fun calculateCPM(v: Int, duration: Duration): Double = v.toDouble() / duration.toMinutes().toDouble()

        fun calculateUSVh(v: Int, duration: Duration): Double = calculateCPM(v, duration) / 60.0 * 10.0 / GEIGER_TUBE_SENSITIVITY

        fun formatVoltage(v: Double?): String = when (v) {
            null -> "---V"
            else -> String.format("%3.0fV", v)
        }

        fun formatUSVh(v: Double?): String = when (v) {
            null -> "-.-- uSv/h"
            else -> String.format("%1.2f uSv/h", v)
        }

        fun formatCPM(v: Double?): String = when (v) {
            null -> "-- CPM"
            else -> String.format("%2.0f CPM", v)
        }
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

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {

        val fd = hardware.frontDisplay
        val dr = deviceReport
        val cr = counterReport

        if (redrawStatic) {
            launch {
                fd.setStaticText(14, "g:")
                fd.setStaticText(14 + 20, "e:")
            }
        }

        if (redrawStatus) {
            launch { fd.setStaticText(0, formatCPM(cr?.lastCPM)) }
            launch { fd.setStaticText(20, formatUSVh(cr?.lastUSVh)) }

            if (cr?.historical != null && cr.lastUSVh != null) {
                launch { fd.setOneLineDiffChart(5 * 8, cr.lastUSVh, cr.historical, 0.01) }
            } else {
                fd.setStaticText(8, "    ")
            }
        }

        launch { fd.setStaticText(16, formatVoltage(deviceReport?.geigerVoltage)) }
        launch { fd.setStaticText(16 + 20, formatVoltage(deviceReport?.eyeVoltage)) }

        launch {
            fd.setStaticText(11 + 20, when (dr?.eyeState) {
                EyeInverterState.DISABLED -> "eD"
                EyeInverterState.HEATING_LIMITED -> "e1"
                EyeInverterState.HEATING_FULL -> "e2"
                EyeInverterState.RUNNING -> "eR"
                null -> "--"
            })
        }

        if (cr != null) {
            launch { fd.setUpperBar(listOf(getSegmentNumber(cr.progress, cr.timeSpan))) }
        } else {
            launch { fd.setUpperBar(emptyList()) }
        }

        Unit
    }

    override fun getMenus(): List<Menu> {
        val optOn = MenuOption("ON")
        val optOff = MenuOption("OFF")

        return listOf(object : Menu("Magic eye") {
            override val options: List<MenuOption>
                get() = listOf(optOn, optOff)

            override suspend fun loadCurrentOption(): MenuOption {
                val eyeState = hardware.geiger.getDeviceState().eyeState
                logger.info { "Eye state is $eyeState." }
                return when (eyeState) {
                    EyeInverterState.DISABLED -> optOff
                    else -> optOn
                }
            }

            override suspend fun saveCurrentOption(current: MenuOption) {
                logger.info { "Magic eye set to $current." }
                hardware.geiger.setEyeConfiguration(when (current) {
                    optOn -> true
                    else -> false
                })
            }
        })
    }

    override suspend fun poolInstantData(): UpdateStatus {
        return try {
            deviceReport = hardware.geiger.getDeviceState()
            UpdateStatus.FULL_SUCCESS
        } catch (e: Exception) {
            deviceReport = null
            logger.error(e) { "Cannot read device state." }
            UpdateStatus.FAILURE
        }
    }

    override suspend fun poolStatusData(): UpdateStatus {
        try {
            val cs = hardware.geiger.getCounterState()

            if (cs.hasCycleEverCompleted && (cs.hasNewCycleStarted || counterReport == null)) {
                val ts = LocalDateTime.now()
                val cpm = calculateCPM(cs.numOfCountsInPreviousCycle, cs.cycleLength)
                val usvh = calculateUSVh(cs.numOfCountsInPreviousCycle, cs.cycleLength)

                logger.info { "Read radioactivity: ${cs.numOfCountsInPreviousCycle}, $usvh uSv/h." }

                listOf(database.insertHistoricalValueAsync(ts, RadioactivityCpm, cpm),
                        database.insertHistoricalValueAsync(ts, RadioactivityUSVH, usvh)).joinAll()

                val historicalRadioactivity = database.getLastHistoricalValuesByHourAsync(ts, RadioactivityUSVH, HISTORIC_VALUES_LENGTH)

                counterReport = CounterReport(cpm, usvh, cs.currentCycleProgress, cs.cycleLength, historicalRadioactivity.await())
                return UpdateStatus.FULL_SUCCESS
            } else {
                val rep = counterReport
                if (rep == null) {
                    counterReport = CounterReport(null, null, cs.currentCycleProgress, cs.cycleLength, null)
                } else {
                    rep.progress = cs.currentCycleProgress
                }

                return UpdateStatus.NO_NEW_DATA
            }
        } catch (e: Exception) {
            counterReport = null
            logger.error(e) { "Cannot read counter state." }
            return UpdateStatus.FAILURE
        }
    }
}