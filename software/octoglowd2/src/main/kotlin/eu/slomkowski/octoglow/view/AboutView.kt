package eu.slomkowski.octoglow.view

import eu.slomkowski.octoglow.hardware.FrontDisplay
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration

class AboutView(
        private val frontDisplay: FrontDisplay
) : FrontDisplayView {

    override suspend fun redrawDisplay() = coroutineScope<Unit> {
        launch { frontDisplay.setStaticText(0, "OCTOGLOW by Michał Słomkowski") }
    }

    override suspend fun poolStateUpdate(): Deferred<Boolean> = coroutineScope { async { false } }

    override fun getPreferredPoolingInterval(): Duration = Duration.ofSeconds(3)
}