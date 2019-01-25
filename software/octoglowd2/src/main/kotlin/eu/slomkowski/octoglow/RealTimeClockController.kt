package eu.slomkowski.octoglow

import eu.slomkowski.octoglow.hardware.ClockDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import java.time.LocalDateTime

fun CoroutineScope.createRealTimeClockController(clockDisplay: ClockDisplay) = launch {
    for (t in ticker(500)) {
        LocalDateTime.now().apply {
            val upperDot = second >= 20
            val lowerDot = second < 20 || second > 40
            clockDisplay.setDisplay(hour, minute, upperDot, lowerDot)
        }
    }
}
