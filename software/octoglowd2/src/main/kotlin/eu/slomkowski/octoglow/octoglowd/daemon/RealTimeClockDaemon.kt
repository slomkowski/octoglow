package eu.slomkowski.octoglow.octoglowd.daemon

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime

class RealTimeClockDaemon(
        config: Config,
        private val hardware: Hardware) : Daemon(config, hardware, logger, Duration.ofMillis(500)) {

    companion object : KLogging()

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
