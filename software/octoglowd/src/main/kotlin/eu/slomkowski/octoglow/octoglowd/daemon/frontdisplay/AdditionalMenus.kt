package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.daemon.BrightnessDaemon
import io.github.oshai.kotlinlogging.KotlinLogging


class BrightnessMenu(private val brightnessDaemon: BrightnessDaemon) : Menu("Brightness") {
    companion object {
        private val logger = KotlinLogging.logger {}

        private val optAuto = MenuOption("AUTO")
        private val optsHard = (1..5).map { MenuOption(it.toString()) }
    }

    override val options: List<MenuOption>
        get() = optsHard.plus(optAuto)

    override suspend fun loadCurrentOption(): MenuOption =
        brightnessDaemon.forced?.let { f -> optsHard.firstOrNull { it.text == f.toString() } }
            ?: optAuto

    override suspend fun saveCurrentOption(current: MenuOption) {
        logger.info { "Setting brightness mode to $current." }
        brightnessDaemon.setForcedMode(current.text.toIntOrNull())
    }
}