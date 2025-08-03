@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.DataSnapshot
import eu.slomkowski.octoglow.octoglowd.DataSnapshotBus
import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.demon.PollingDemon
import io.github.oshai.kotlinlogging.KLogger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

abstract class DataHarvester(
    logger: KLogger,
    pollingInterval: Duration,
    private val dataSnapshotBus: DataSnapshotBus,
) : PollingDemon(logger, pollingInterval) {

    abstract suspend fun pollForNewData(now: Instant)

    protected suspend fun publish(dataSnapshot: Snapshot) {
        dataSnapshotBus.publish(dataSnapshot)
    }

    final override suspend fun poll() {
        // todo przekazywać jakoś czas z zewnątrz?
        pollForNewData(Clock.System.now())
    }
}