@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.datacollectors

import eu.slomkowski.octoglow.octoglowd.HistoricalValuesEvents
import eu.slomkowski.octoglow.octoglowd.RadioactivityCpm
import eu.slomkowski.octoglow.octoglowd.RadioactivityUSVH
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.GeigerView.Companion.calculateCPM
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.GeigerView.Companion.calculateUSVh
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class GeigerMeasurementReport(
    override val timestamp: Instant,
    val currentCycleProgress: Duration?,
    override val cycleLength: Duration?,
    override val values: List<Measurement>,
) : MeasurementReport

class GeigerDataCollector(
    private val hardware: Hardware,
    eventBus: HistoricalValuesEvents,
) : DataCollector(logger, 7.seconds, eventBus) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun pollForNewData(now: Instant): Unit = coroutineScope {
        publishPacket(
            try {
                val cs = hardware.geiger.getCounterState()

                val measurementValues = if (cs.hasCycleEverCompleted && (cs.hasNewCycleStarted)) {
                    val cpm = calculateCPM(cs.numOfCountsInPreviousCycle, cs.cycleLength)
                    val uSvh = calculateUSVh(cs.numOfCountsInPreviousCycle, cs.cycleLength)

                    logger.info {
                        String.format(
                            "Read radioactivity: %d counts = %.2f uSv/h.",
                            cs.numOfCountsInPreviousCycle,
                            uSvh
                        )
                    }

                    listOf(
                        StandardMeasurement(RadioactivityCpm, Result.success(cpm)),
                        StandardMeasurement(RadioactivityUSVH, Result.success(uSvh)),
                    )
                } else {
                    emptyList()
                }

                GeigerMeasurementReport(
                    now,
                    cs.currentCycleProgress,
                    cs.cycleLength,
                    measurementValues,
                )
            } catch (e: Exception) {
                logger.error(e) { "Cannot read Geiger counter state." }
                GeigerMeasurementReport(
                    now,
                    null,
                    null,
                    listOf(
                        StandardMeasurement(RadioactivityCpm, Result.failure(e)),
                        StandardMeasurement(RadioactivityUSVH, Result.failure(e))
                    )
                )
            })
    }
}