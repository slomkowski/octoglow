package eu.slomkowski.octoglow.octoglowd.daemon.view

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate

class CalendarView(
        private val config: Config,
        private val hardware: Hardware)
    : FrontDisplayView("Calendar", Duration.ofMinutes(3), Duration.ofMinutes(1)) {

    override suspend fun poolStatusData(): UpdateStatus {
        //todo check speed how it loads the data from
        //todo jollyday, lista imienin

        return UpdateStatus.FULL_SUCCESS
    }

    override suspend fun poolInstantData(): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            //todo
            launch { fd.setStaticText(0, LocalDate.now().toString()) }
        }

        if (redrawStatus) {
            //todo
        }
    }
}