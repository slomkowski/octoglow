package eu.slomkowski.octoglow.octoglowd

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DataSnapshotBus {
    /**
     * The replay buffer exists so that views subscribing late - `main.kt` staggers demon startup by a
     * few seconds - still see data harvested before they were listening. It does not need to be deep
     * for that; it only has to cover the startup window, and every retained snapshot (a full Todoist
     * or SimpleMonitor payload, for instance) is pinned for as long as it sits there.
     *
     * The spare capacity with [BufferOverflow.DROP_OLDEST] is what keeps a slow subscriber from
     * stalling every harvester: with the default SUSPEND policy, `publish` blocks once the slowest of
     * the ~18 subscribers falls a bufferful behind.
     */
    private val _snapshots = MutableSharedFlow<Snapshot>(
        replay = 32,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val snapshots = _snapshots.asSharedFlow()

    suspend fun publish(dataSnapshot: Snapshot) {
        _snapshots.emit(dataSnapshot)
    }
}

class CommandBus {
    /**
     * No replay: these are one-off actions, not state. Replaying them meant a subscriber that started
     * late re-executed up to a hundred historical dial presses and backlight toggles.
     */
    private val _commands = MutableSharedFlow<Command>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands = _commands.asSharedFlow()

    suspend fun publish(cmd: Command) {
        _commands.emit(cmd)
    }
}

