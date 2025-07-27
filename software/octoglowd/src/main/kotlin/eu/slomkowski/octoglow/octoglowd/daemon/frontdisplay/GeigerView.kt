package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.EyeInverterState
import eu.slomkowski.octoglow.octoglowd.hardware.GeigerDeviceState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit


class GeigerView(
    private val config: Config,
    private val database: DatabaseLayer,
    hardware: Hardware,
) : FrontDisplayView(
    hardware,
    "Geiger counter",
    7.seconds,
    3.seconds,
) {
    override val preferredDisplayTime: Duration = 11.seconds

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val HISTORIC_VALUES_LENGTH = 4 * 5 - 1

        private const val GEIGER_TUBE_SENSITIVITY = 25.0

        private const val RADMON_ORG_API_URL = "https://radmon.org/radmon.php"

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

        val phpDateTimeFormat = DateTimeComponents.Format {
            year()
            char('-')
            monthNumber()
            char('-')
            dayOfMonth()
            char(' ')
            hour()
            char(':')
            minute()
            char(':')
            second()
        }

        suspend fun submitToRadmonOrg(
            username: String,
            password: String,
            ts: Instant,
            cpm: Double,
        ) {
            val tsStr = ts.format(phpDateTimeFormat)
            val cpmInt = cpm.roundToInt()
            logger.info { "Submitting to radmon.org measurement of $cpmInt CPM (user '$username')." }

            try {
                httpClient.get(RADMON_ORG_API_URL) {
                    timeout {
                        requestTimeoutMillis = 20_000
                    }
                    parameter("function", "submit")
                    parameter("user", username)
                    parameter("password", password)
                    parameter("datetime", tsStr)
                    parameter("value", cpmInt)
                    parameter("unit", "CPM")
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to submit data to radmon.org" }
            }
        }

        suspend fun getLastReading(username: String): Double {
            val responseStr = httpClient.get(RADMON_ORG_API_URL) {
                timeout {
                    requestTimeoutMillis = 20_000
                }
                parameter("function", "lastreading")
                parameter("user", username)
            }.bodyAsText().trim()
            logger.info { "Last raw reading: $responseStr." }
            return Regex("(\\d+) CPM").find(responseStr)?.let { it.groupValues[1].toDouble() } ?: error("Failed to parse $responseStr")
        }
    }

    data class CounterReport(
        val lastCPM: Double?, // counts-per-minute
        val lastUSVh: Double?, // uSv/h

        @Volatile
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

    @Volatile
    private var counterReport: CounterReport? = null

    @Volatile
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

    override suspend fun pollInstantData(now: Instant): UpdateStatus {
        return try {
            deviceReport = hardware.geiger.getDeviceState()
            UpdateStatus.FULL_SUCCESS
        } catch (e: Exception) {
            deviceReport = null
            logger.error(e) { "Cannot read device state." }
            UpdateStatus.FAILURE
        }
    }

    override suspend fun pollStatusData(now: Instant): UpdateStatus = coroutineScope {
        try {
            val cs = hardware.geiger.getCounterState()

            return@coroutineScope if (cs.hasCycleEverCompleted && (cs.hasNewCycleStarted || counterReport == null)) {
                val cpm = calculateCPM(cs.numOfCountsInPreviousCycle, cs.cycleLength)
                val uSvh = calculateUSVh(cs.numOfCountsInPreviousCycle, cs.cycleLength)

                logger.info {
                    String.format(
                        "Read radioactivity: %d counts = %.2f uSv/h.",
                        cs.numOfCountsInPreviousCycle,
                        uSvh
                    )
                }

                if (config.radmon?.enabled == true) {
                    launch {
                        submitToRadmonOrg(
                            config.radmon.username,
                            config.radmon.password,
                            now,
                            cpm,
                        )
                    }
                }

                listOf(
                    database.insertHistoricalValueAsync(now, RadioactivityCpm, cpm),
                    database.insertHistoricalValueAsync(now, RadioactivityUSVH, uSvh),
                ).joinAll()

                val historicalRadioactivity =
                    database.getLastHistoricalValuesByHourAsync(now, RadioactivityUSVH, HISTORIC_VALUES_LENGTH)

                counterReport =
                    CounterReport(cpm, uSvh, cs.currentCycleProgress, cs.cycleLength, historicalRadioactivity.await())

                UpdateStatus.FULL_SUCCESS
            } else {
                val rep = counterReport
                if (rep == null) {
                    counterReport = CounterReport(null, null, cs.currentCycleProgress, cs.cycleLength, null)
                } else {
                    rep.progress = cs.currentCycleProgress
                }

                UpdateStatus.NO_NEW_DATA
            }
        } catch (e: Exception) {
            counterReport = null
            logger.error(e) { "Cannot read counter state." }
            return@coroutineScope UpdateStatus.FAILURE
        }
    }
}