package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.daemon.view.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.daemon.view.UpdateStatus
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime

class FrontDisplayDaemon(
        private val hardware: Hardware,
        private val frontDisplayViews: List<FrontDisplayView>)
    : Daemon(Duration.ofMillis(100)) {

    data class ViewInfo(
            val view: FrontDisplayView,
            var lastPooledStatusUpdate: LocalDateTime?,
            var lastPooledInstantUpdate: LocalDateTime?)

    companion object : KLogging() {
        fun updateViewIndex(current: Int, delta: Int, size: Int): Int {
            require(current in 0..(size - 1))
            return (10000 * size + current + delta) % size
        }
    }

    override suspend fun pool() = coroutineScope {
        require(frontDisplayViews.isNotEmpty())

        var currentViewIdx = 0
        val views = frontDisplayViews.map { ViewInfo(it, null, null) }

        hardware.frontDisplay.clear()

        for (t in ticker(100)) {

            val now = LocalDateTime.now()
            val currentView = views[currentViewIdx]

            views.filter {
                it.lastPooledInstantUpdate?.plus(it.view.preferredInstantPoolingInterval)?.isBefore(now) ?: true
            }.forEach {
                it.lastPooledInstantUpdate = now
                launch {
                    val status = it.view.poolInstantData()
                    if (it == currentView && status != UpdateStatus.NO_NEW_DATA) {
                        launch { currentView.view.redrawDisplay(false, false) }
                    }
                }
            }

            views.filter {
                it.lastPooledStatusUpdate?.plus(it.view.preferredStatusPoolingInterval)?.isBefore(now) ?: true
            }.forEach {
                it.lastPooledStatusUpdate = now
                launch {
                    val status = it.view.poolStatusData()
                    if (it == currentView && status != UpdateStatus.NO_NEW_DATA) {
                        launch { currentView.view.redrawDisplay(false, true) }
                    }
                }
            }

            val buttonReport = hardware.frontDisplay.getButtonReport()

            if (buttonReport.encoderDelta != 0) {
                logger.debug { "Encoder delta: ${buttonReport.encoderDelta}." }
            }

            val newViewIndex = updateViewIndex(currentViewIdx, buttonReport.encoderDelta, views.size)

            if (newViewIndex != currentViewIdx) {
                val newCurrentView = views[newViewIndex].view

                logger.debug { "Switched view to ${newCurrentView.name}." }

                launch {
                    hardware.frontDisplay.clear()
                    newCurrentView.redrawDisplay(true, true) // todo add redraw when still on the same display
                }

                currentViewIdx = newViewIndex
            }
        }
    }

}