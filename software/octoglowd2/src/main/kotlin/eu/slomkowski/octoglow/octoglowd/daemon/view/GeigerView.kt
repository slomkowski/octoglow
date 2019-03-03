package eu.slomkowski.octoglow.octoglowd.daemon.view

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.DatabaseLayer
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import mu.KLogging
import java.time.Duration

class GeigerView(
        private val config: Config,
        private val database: DatabaseLayer,
        private val hardware: Hardware) : FrontDisplayView {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14

    }

    override suspend fun redrawDisplay() = coroutineScope {
    }

    override suspend fun poolStateUpdateAsync(): Deferred<UpdateStatus> = coroutineScope {
        TODO()
    }

    override fun getPreferredPoolingInterval(): Duration = Duration.ofMinutes(2) // change to 5 min

    override val name: String
        get() = "Geiger Counter"
}