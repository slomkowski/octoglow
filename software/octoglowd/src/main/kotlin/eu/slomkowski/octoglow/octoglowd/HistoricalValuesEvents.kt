package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.datacollectors.MeasurementReport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class HistoricalValuesEvents {
    private val _events = MutableSharedFlow<MeasurementReport>(replay = 100)
    val events = _events.asSharedFlow()

    suspend fun produceEvent(event: MeasurementReport) {
        _events.emit(event)
    }
}
