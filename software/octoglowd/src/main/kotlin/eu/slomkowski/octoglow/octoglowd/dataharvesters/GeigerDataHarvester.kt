@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.GeigerView.Companion.calculateCPM
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.GeigerView.Companion.calculateUSVh
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class GeigerDataSnapshot(
    override val timestamp: Instant,
    val currentCycleProgress: Duration?,
    override val cycleLength: Duration,
    override val values: List<DataSample>,
) : DataSnapshot

class GeigerDataHarvester(
    private val hardware: Hardware,
    eventBus: DataSnapshotBus,
) : DataHarvester(logger, 7.seconds, eventBus) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override suspend fun pollForNewData(now: Instant): Unit = coroutineScope {
        publish(
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
                        StandardDataSample(RadioactivityCpm, Result.success(cpm)),
                        StandardDataSample(RadioactivityUSVH, Result.success(uSvh)),
                    )
                } else {
                    emptyList()
                }

                GeigerDataSnapshot(
                    now,
                    cs.currentCycleProgress,
                    cs.cycleLength,
                    measurementValues,
                )
            } catch (e: Exception) {
                logger.error(e) { "Cannot read Geiger counter state." }
                GeigerDataSnapshot(
                    now,
                    null,
                    10.minutes, // this is dummy value
                    listOf(
                        StandardDataSample(RadioactivityCpm, Result.failure(e)),
                        StandardDataSample(RadioactivityUSVH, Result.failure(e))
                    )
                )
            })
    }
}