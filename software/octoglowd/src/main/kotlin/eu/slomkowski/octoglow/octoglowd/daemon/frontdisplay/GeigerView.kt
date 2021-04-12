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
import kotlinx.datetime.Instant
import mu.KLogging
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
class GeigerView(
    private val database: DatabaseLayer,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "Geiger counter",
    7.seconds,
    3.seconds,
    11.seconds
) {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 4 * 5 - 1

        private const val GEIGER_TUBE_SENSITIVITY = 25.0

        fun calculateCPM(v: Int, duration: Duration): Double = v.toDouble() / duration.inMinutes

        fun calculateUSVh(v: Int, duration: Duration): Double =
            calculateCPM(v, duration) / 60.0 * 10.0 / GEIGER_TUBE_SENSITIVITY

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
        val historical: List<Double?>?
    ) {
        init {
            lastCPM?.let { it > 0 }
            lastUSVh?.let { it > 0 }
            require(progress <= timeSpan)
            historical?.let { require(it.size == HISTORIC_VALUES_LENGTH) }
        }
    }

    private var counterReport: CounterReport? = null

    private var deviceReport: GeigerDeviceState? = null

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {

            val fd = hardware.frontDisplay
            val dr = deviceReport
            val cr = counterReport

            if (redrawStatic) {
                launch {
                    fd.setStaticText(14, "g:")
                }
            }

            if (redrawStatus) {
                launch {
                    fd.setStaticText(0, formatCPM(cr?.lastCPM))
                    fd.setStaticText(20, formatUSVh(cr?.lastUSVh))
                }

                launch {
                    if (cr?.historical != null && cr.lastUSVh != null) {
                        fd.setOneLineDiffChart(5 * 8, cr.lastUSVh, cr.historical, 0.01)
                    } else {
                        fd.setStaticText(8, "    ")
                    }
                }
            }

            launch { fd.setStaticText(16, formatVoltage(deviceReport?.geigerVoltage)) }
            launch {
                fd.setStaticText(
                    16 + 20, when (dr?.eyeState) {
                        EyeInverterState.DISABLED -> "    "
                        else -> formatVoltage(deviceReport?.eyeVoltage)
                    }
                )
            }

            launch {
                fd.setStaticText(
                    11 + 20, when (dr?.eyeState) {
                        EyeInverterState.DISABLED -> "eD"
                        EyeInverterState.HEATING_LIMITED -> "e1 e:"
                        EyeInverterState.HEATING_FULL -> "e2 e:"
                        EyeInverterState.RUNNING -> "eR e:"
                        null -> "--"
                    }
                )

                if (cr != null) {
                    fd.setUpperBar(listOf(getSegmentNumber(cr.progress, cr.timeSpan)))
                } else {
                    fd.setUpperBar(emptyList())
                }
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
                hardware.geiger.setEyeConfiguration(
                    when (current) {
                        optOn -> true
                        else -> false
                    }
                )
            }
        })
    }

    override suspend fun poolInstantData(now: Instant): UpdateStatus {
        return try {
            deviceReport = hardware.geiger.getDeviceState()
            UpdateStatus.FULL_SUCCESS
        } catch (e: Exception) {
            deviceReport = null
            logger.error(e) { "Cannot read device state." }
            UpdateStatus.FAILURE
        }
    }

    override suspend fun poolStatusData(now: Instant): UpdateStatus {
        try {
            val cs = hardware.geiger.getCounterState()

            if (cs.hasCycleEverCompleted && (cs.hasNewCycleStarted || counterReport == null)) {
                val cpm = calculateCPM(cs.numOfCountsInPreviousCycle, cs.cycleLength)
                val uSvh = calculateUSVh(cs.numOfCountsInPreviousCycle, cs.cycleLength)

                logger.info(
                    String.format(
                        "Read radioactivity: %d counts = %.2f uSv/h.",
                        cs.numOfCountsInPreviousCycle,
                        uSvh
                    )
                )

                listOf(
                    database.insertHistoricalValueAsync(now, RadioactivityCpm, cpm),
                    database.insertHistoricalValueAsync(now, RadioactivityUSVH, uSvh)
                ).joinAll()

                val historicalRadioactivity =
                    database.getLastHistoricalValuesByHourAsync(now, RadioactivityUSVH, HISTORIC_VALUES_LENGTH)

                counterReport =
                    CounterReport(cpm, uSvh, cs.currentCycleProgress, cs.cycleLength, historicalRadioactivity.await())
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