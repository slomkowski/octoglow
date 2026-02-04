package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.demon.BrightnessDemon
import eu.slomkowski.octoglow.octoglowd.demon.Demon
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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

abstract class ExportableSwitchMenu(
    private val logger: KLogger,
    private val humanReadableName: String,
    private val snapshotBus: DataSnapshotBus,
    private val commandBus: CommandBus,
) : Menu(humanReadableName), Demon {

    companion object {
        private val availableOptions = listOf(optOn, optOff)
    }

    private val enabledStateFlow = MutableStateFlow(false)

    override val options: List<MenuOption> = availableOptions

    abstract suspend fun isStateChanged(snapshot: Snapshot): Boolean

    abstract fun createChangeStateCommand(enabled: Boolean): ChangeStateCommand

    override suspend fun loadCurrentOption(): MenuOption {
        val enabled = enabledStateFlow.value
        logger.debug { "$humanReadableName state is $enabled." }
        return if (enabled) optOn else optOff
    }

    override suspend fun saveCurrentOption(current: MenuOption) {
        logger.info { "$humanReadableName set to $current." }
        commandBus.publish(
            createChangeStateCommand(
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
                if (isStateChanged(snapshot)) {
                    snapshot as StateChanged
                    enabledStateFlow.emit(snapshot.enabled)
                }
            }
        })
    }
}

class MagicEyeMenu(
    snapshotBus: DataSnapshotBus,
    commandBus: CommandBus,
) : ExportableSwitchMenu(logger, "Magic eye", snapshotBus, commandBus) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun isStateChanged(snapshot: Snapshot) = snapshot is MagicEyeStateChanged

    override fun createChangeStateCommand(enabled: Boolean) = MagicEyeChangeStateCommand(enabled)
}

class BacklightMenu(
    snapshotBus: DataSnapshotBus,
    commandBus: CommandBus,
) : ExportableSwitchMenu(logger, "Backlight", snapshotBus, commandBus) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun isStateChanged(snapshot: Snapshot) = snapshot is BacklightStateChanged

    override fun createChangeStateCommand(enabled: Boolean) = BacklightChangeStateCommand(enabled)
}
