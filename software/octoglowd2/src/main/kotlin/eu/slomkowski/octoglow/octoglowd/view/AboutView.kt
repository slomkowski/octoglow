package eu.slomkowski.octoglow.octoglowd.view

import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration

class AboutView(
        private val hardware: Hardware
) : FrontDisplayView {

    override val name: String
        get() = "About"

    override suspend fun redrawDisplay() = coroutineScope<Unit> {
        launch { hardware.frontDisplay.setStaticText(0, "OCTOGLOW by Michał Słomkowski") }
    }

    override suspend fun poolStateUpdate(): Deferred<Boolean> = coroutineScope { async { false } }

    override fun getPreferredPoolingInterval(): Duration = Duration.ofSeconds(3)
}