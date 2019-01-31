package eu.slomkowski.octoglow.octoglowd.controller

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import java.time.Duration
import java.time.LocalDateTime

class RealTimeClockController(
        private val hardware: Hardware) : Controller(Duration.ofMillis(500)) {
    override suspend fun pool() {
        LocalDateTime.now().apply {
            val upperDot = second >= 20
            val lowerDot = second < 20 || second > 40
            hardware.clockDisplay.setDisplay(hour, minute, upperDot, lowerDot)
        }
    }
}
