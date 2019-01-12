package eu.slomkowski.octoglow

import eu.slomkowski.octoglow.hardware.ClockDisplay
import eu.slomkowski.octoglow.hardware.FrontDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import java.time.LocalDateTime

fun CoroutineScope.createFrontDisplayController(frontDisplay: FrontDisplay) = launch {
    frontDisplay.clear()
    for (t in ticker(100)) {
       frontDisplay.getButtonReport().let {
           frontDisplay.setStaticText(2, "${it.button}")
       }
    }
}