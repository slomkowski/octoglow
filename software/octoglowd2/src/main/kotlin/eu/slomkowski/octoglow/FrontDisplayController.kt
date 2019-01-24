package eu.slomkowski.octoglow

import eu.slomkowski.octoglow.hardware.FrontDisplay
import eu.slomkowski.octoglow.view.FrontDisplayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.time.LocalDateTime

data class ViewInfo(
        val view: FrontDisplayView,
        var lastPooled: LocalDateTime?)

fun updateViewIndex(current: Int, delta: Int, size: Int): Int {
    require(current in 0..(size - 1))
    return (10000 * size + current + delta) % size
}

fun CoroutineScope.createFrontDisplayController(frontDisplay: FrontDisplay,
                                                frontDisplayViews: List<FrontDisplayView>) = launch {
    require(frontDisplayViews.isNotEmpty())
    val logger = KotlinLogging.logger("FrontDisplayController")

    var currentViewIdx = 0
    val views = frontDisplayViews.map { ViewInfo(it, null) }

    frontDisplay.clear()

    for (t in ticker(100)) {

        val now = LocalDateTime.now()

        views.filter { it.lastPooled?.plus(it.view.getPreferredPoolingInterval())?.isBefore(now) ?: true }.forEach {
            it.view.poolStateUpdate()
            it.lastPooled = now
        }

        val buttonReport = frontDisplay.getButtonReport()

        val newViewIndex = updateViewIndex(currentViewIdx, buttonReport.encoderDelta, views.size)

        if (newViewIndex != currentViewIdx) {
            val newCurrentView = views[newViewIndex].view

            logger.debug { "Switched view to $newCurrentView." }

            launch {
                frontDisplay.clear()
                newCurrentView.redrawDisplay()
            }

            currentViewIdx = newViewIndex
        }
    }
}
