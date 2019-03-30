package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import java.time.Duration
import java.time.LocalDateTime

class RealTimeClockDaemon(
        private val hardware: Hardware) : Daemon(Duration.ofMillis(500)) {
    override suspend fun pool() {
        setClockDisplayContent(LocalDateTime.now())
    }

    suspend fun setClockDisplayContent(now: LocalDateTime) = now.apply {
        val upperDot = second >= 20
        val lowerDot = second < 20 || second > 40
        hardware.clockDisplay.setDisplay(hour, minute, upperDot, lowerDot)
    }
}
