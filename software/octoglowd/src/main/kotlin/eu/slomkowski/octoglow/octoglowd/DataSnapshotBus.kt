package eu.slomkowski.octoglow.octoglowd

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DataSnapshotBus {
    private val _snapshots = MutableSharedFlow<DataSnapshot>(replay = 100)
    val snapshots = _snapshots.asSharedFlow()

    suspend fun publish(dataSnapshot: DataSnapshot) {
        _snapshots.emit(dataSnapshot)
    }
}
