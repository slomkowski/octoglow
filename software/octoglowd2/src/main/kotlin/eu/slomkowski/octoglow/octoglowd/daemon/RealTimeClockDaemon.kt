package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import java.time.Duration
import java.time.LocalDateTime

class RealTimeClockDaemon(
        private val hardware: Hardware) : Daemon(Duration.ofMillis(500)) {

    data class DisplayContent(
            val hour: Int,
            val minute: Int,
            val upperDot: Boolean,
            val lowerDot: Boolean) {
        constructor(dt: LocalDateTime) : this(dt.hour, dt.minute, dt.second >= 20, dt.second < 20 || dt.second > 40)
    }

    private var previousDisplayContent: DisplayContent? = null

    override suspend fun pool() {
        val newDisplayContent = DisplayContent(LocalDateTime.now())

        if (newDisplayContent != previousDisplayContent) {
            newDisplayContent.apply {
                hardware.clockDisplay.setDisplay(hour, minute, upperDot, lowerDot)
            }
            previousDisplayContent = newDisplayContent
        }
    }
}
