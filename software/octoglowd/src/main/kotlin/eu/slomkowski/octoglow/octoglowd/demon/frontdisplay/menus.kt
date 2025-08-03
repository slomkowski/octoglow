package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.demon.BrightnessDemon
import eu.slomkowski.octoglow.octoglowd.demon.Demon
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


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

private val optOn = MenuOption("ON")
private val optOff = MenuOption("OFF")

class BooleanChangeableSettingMenu(
    private val database: DatabaseDemon,
    private val key: ChangeableSetting,
    text: String
) : Menu(text) {
    companion object {
        private val logger = KotlinLogging.logger {}
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

class MagicEyeMenu(
    private val snapshotBus: DataSnapshotBus,
    private val commandBus: CommandBus,
) : Menu("Magic eye"), Demon {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Volatile
    private var eyeEnabled: Boolean? = null

    override val options: List<MenuOption>
        get() = listOf(optOn, optOff)

    override suspend fun loadCurrentOption(): MenuOption {
        logger.debug { "Eye state is $eyeEnabled." }
        return if (eyeEnabled == true) optOn else optOff
    }

    override suspend fun saveCurrentOption(current: MenuOption) {
        logger.info { "Magic eye set to $current." }
        commandBus.publish(
            MagicEyeCommand(
                when (current) {
                    optOn -> true
                    else -> false
                }
            )
        )
    }

    override fun createJobs(scope: CoroutineScope): List<Job> {
        return listOf(scope.launch {
            snapshotBus.snapshots.collect { snapshot ->
                if (snapshot is MagicEyeStateChanged) {
                    eyeEnabled = snapshot.enabled
                }
            }
        })
    }
}