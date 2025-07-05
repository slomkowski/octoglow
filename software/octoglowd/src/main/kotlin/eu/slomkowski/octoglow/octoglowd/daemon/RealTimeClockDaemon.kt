package eu.slomkowski.octoglow.octoglowd.daemon


import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.now
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

class RealTimeClockDaemon(
    private val hardware: Hardware,
) : Daemon(logger, 200.milliseconds) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    data class DisplayContent(
        val hour: Int,
        val minute: Int,
        val upperDot: Boolean,
        val lowerDot: Boolean,
    ) {
        companion object {
            fun ofTimestamp(dt: LocalDateTime): DisplayContent = when (dt.second % 2) {
                0 -> DisplayContent(dt.hour, dt.minute, dt.second >= 20, dt.second < 20 || dt.second > 40)
                else -> DisplayContent(dt.hour, dt.minute, upperDot = false, lowerDot = false)
            }
        }
    }

    @Volatile
    private var previousDisplayContent: DisplayContent? = null

    override suspend fun pool() {
        val newDisplayContent = DisplayContent.ofTimestamp(now().toLocalDateTime(TimeZone.currentSystemDefault()))

        if (newDisplayContent != previousDisplayContent) {
            newDisplayContent.apply {
                hardware.clockDisplay.setDisplay(hour, minute, upperDot, lowerDot)
            }
            previousDisplayContent = newDisplayContent
        }
    }
}
