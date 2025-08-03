package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.dataharvesters.GeigerDataSnapshot
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.GeigerView.CounterReport
import eu.slomkowski.octoglow.octoglowd.hardware.EyeInverterState
import eu.slomkowski.octoglow.octoglowd.hardware.GeigerDeviceState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@OptIn(ExperimentalTime::class)
class GeigerView(
    private val database: DatabaseDemon,
    hardware: Hardware,
) : FrontDisplayView<CounterReport, GeigerDeviceState>(
    hardware,
    "Geiger counter",
    2.seconds,
    logger,
) {
    override fun preferredDisplayTime(status: CounterReport?) = 11.seconds

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val HISTORIC_VALUES_LENGTH = 4 * 5 - 1

        private const val GEIGER_TUBE_SENSITIVITY = 25.0

        fun calculateCPM(v: Int, duration: Duration): Double = v.toDouble() / duration.toDouble(DurationUnit.MINUTES)

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

        val timeSpan: Duration?,
        val historical: List<Double?>?,
    ) {
        init {
            lastCPM?.let { it > 0 }
            lastUSVh?.let { it > 0 }
            historical?.let { require(it.size == HISTORIC_VALUES_LENGTH) }
        }
    }

    @Volatile
    private var progress: Duration? = null

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: CounterReport?,
        instant: GeigerDeviceState?
    ): Unit = coroutineScope {

        val fd = hardware.frontDisplay

        if (redrawStatic) {
            launch {
                fd.setStaticText(14, "g:")
            }
        }

        if (redrawStatus) {
            launch {
                fd.setStaticText(0, formatCPM(status?.lastCPM))
                fd.setStaticText(20, formatUSVh(status?.lastUSVh))

                if (status?.historical != null && status.lastUSVh != null) {
                    fd.setOneLineDiffChart(5 * 8, status.lastUSVh, status.historical, 0.01)
                } else {
                    fd.setStaticText(8, "    ")
                }
            }
        }

        launch { fd.setStaticText(16, formatVoltage(instant?.geigerVoltage)) }
        launch {
            fd.setStaticText(
                16 + 20, when (instant?.eyeState) {
                    EyeInverterState.DISABLED -> "    "
                    else -> formatVoltage(instant?.eyeVoltage)
                }
            )
        }

        launch {
            fd.setStaticText(
                11 + 20, when (instant?.eyeState) {
                    EyeInverterState.DISABLED -> "eD"
                    EyeInverterState.HEATING_LIMITED -> "e1 e:"
                    EyeInverterState.HEATING_FULL -> "e2 e:"
                    EyeInverterState.RUNNING -> "eR e:"
                    null -> "--"
                }
            )

            val pr = progress
            if (status?.timeSpan != null && pr != null) {
                fd.setUpperBar(listOf(getSegmentNumber(pr, status.timeSpan)))
            } else {
                fd.setUpperBar(emptyList())
            }
        }

        Unit
    }

    override suspend fun pollForNewInstantData(now: Instant, oldInstant: GeigerDeviceState?): UpdateStatus {
        return try {
            UpdateStatus.NewData(hardware.geiger.getDeviceState())
        } catch (e: Exception) {
            logger.error(e) { "Cannot read device state." }
            UpdateStatus.NewData(null)
        }
    }

    override suspend fun onNewDataSnapshot(snapshot: Snapshot, oldStatus: CounterReport?): UpdateStatus {
        if (snapshot !is GeigerDataSnapshot) {
            return UpdateStatus.NoNewData
        }

        progress = snapshot.currentCycleProgress // todo nie wrzucać jakoś do devicestatus?

        val valueCpm = snapshot.values.firstOrNull { it.type == RadioactivityCpm }?.value
        val valueUsVh = snapshot.values.firstOrNull { it.type == RadioactivityUSVH }?.value

        if (valueCpm == null && valueUsVh == null && oldStatus?.timeSpan != null) {
            // no updates on the radioactivity values, just the progress
            return UpdateStatus.NoNewData
        }

        val historicalRadioactivity =
            database.getLastHistoricalValuesByHourAsync(snapshot.timestamp, RadioactivityUSVH, HISTORIC_VALUES_LENGTH)

        return UpdateStatus.NewData(
            CounterReport(
                valueCpm?.getOrNull(),
                valueUsVh?.getOrNull(),
                snapshot.cycleLength,
                historicalRadioactivity.await(),
            )
        )
    }
}