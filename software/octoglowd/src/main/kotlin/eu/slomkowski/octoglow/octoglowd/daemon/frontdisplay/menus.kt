package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.ChangeableSetting
import eu.slomkowski.octoglow.octoglowd.DatabaseLayer
import eu.slomkowski.octoglow.octoglowd.daemon.BrightnessDemon
import io.github.oshai.kotlinlogging.KotlinLogging


data class MenuOption(val text: String) {
    init {
        require(text.isNotBlank())
        require(text.length < 14)
    }

    override fun toString(): String = text
}

abstract class Menu(val text: String) {
    init {
        require(text.isNotBlank())
        require(text.length <= 16)
    }

    abstract val options: List<MenuOption>

    abstract suspend fun loadCurrentOption(): MenuOption

    abstract suspend fun saveCurrentOption(current: MenuOption)

    override fun toString(): String = text
}

class BooleanChangeableSettingMenu(
    private val database: DatabaseLayer,
    private val key: ChangeableSetting,
    text: String
) : Menu(text) {
    companion object {
        private val logger = KotlinLogging.logger {}

        private val optOn = MenuOption("ON")
        private val optOff = MenuOption("OFF")
    }

    override val options: List<MenuOption>
        get() = listOf(optOn, optOff)

    override suspend fun loadCurrentOption(): MenuOption = when (database.getChangeableSettingAsync(key).await()) {
        false.toString() -> optOff
        else -> optOn
    }

    override suspend fun saveCurrentOption(current: MenuOption) {
        database.setChangeableSettingAsync(key, (current == optOn).toString())
        logger.info { "$key set to $current." }
    }
}

class BrightnessMenu(private val brightnessDaemon: BrightnessDemon) : Menu("Brightness") {
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