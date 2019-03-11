package eu.slomkowski.octoglow.octoglowd.daemon.view

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration

class AboutView(
        private val hardware: Hardware
) : FrontDisplayView {

    override val name: String
        get() = "About"

    override suspend fun redrawDisplay(firstTime: Boolean) = coroutineScope<Unit> {
        launch { hardware.frontDisplay.setStaticText(0, "OCTOGLOW by Michał Słomkowski") }
    }

    override suspend fun poolStateUpdate(): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override fun getPreferredPoolingInterval(): Duration = Duration.ofSeconds(3)
}