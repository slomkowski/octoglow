package eu.slomkowski.octoglow.octoglowd.demon


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.EyeInverterState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


abstract class MqttExportableSwitchDemon(
    private val logger: KLogger,
    private val snapshotBus: DataSnapshotBus,
    private val commandBus: CommandBus,
) : Demon {

    private val stateFlow = lazy {
        MutableStateFlow(runBlocking {
            retrieveState()
        })
    }

    abstract val humanReadableName: String

    abstract suspend fun retrieveState(): Boolean

    abstract suspend fun setState(value: Boolean)

    abstract fun createStateChangedEvent(enabled: Boolean): StateChanged

    abstract fun isToggleStateCommand(command: Command): Boolean

    abstract fun isChangeStateCommand(command: Command): Boolean

    abstract fun isPublishStateCommand(command: Command): Boolean

    override fun createJobs(scope: CoroutineScope): List<Job> = listOf(scope.launch {

        queryAndEmitState()

        commandBus.commands.collect { command ->
            if (isToggleStateCommand(command)) {
                command as ToggleStateCommand
                setState(!stateFlow.value.value)
            } else if (isChangeStateCommand(command)) {
                command as ChangeStateCommand
                setState(command.enabled)
                queryAndEmitState()
            } else if (isPublishStateCommand(command)) {
                command as PublishStateCommand
                logger.info { "Publishing $humanReadableName state on request." }
                snapshotBus.publish(createStateChangedEvent(stateFlow.value.value))
            }
        }
    }, scope.launch {
        stateFlow.value.collect { enabled ->
            logger.info { "${humanReadableName.replaceFirstChar { it.uppercaseChar() }} enabled: $enabled, publishing its state." }
            snapshotBus.publish(createStateChangedEvent(enabled))
        }
    }, scope.launch {
        while (isActive) {
            queryAndEmitState()
            delay(800.milliseconds)
        }
    })

    suspend fun queryAndEmitState() {
        val enabled = try {
            retrieveState()
        } catch (e: Exception) {
            logger.error(e) { "Error checking $humanReadableName state. Assuming it is disabled" }
            false
        }

        stateFlow.value.emit(enabled)
    }
}

@OptIn(ExperimentalTime::class)
class MagicEyeDemon(
    private val hardware: Hardware,
    snapshotBus: DataSnapshotBus,
    commandBus: CommandBus,
    private val clock: Clock = Clock.System,
) : MqttExportableSwitchDemon(logger, snapshotBus, commandBus) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override val humanReadableName = "magic eye"

    override suspend fun retrieveState() = when (hardware.geiger.getDeviceState().eyeState) {
        EyeInverterState.DISABLED -> false
        else -> true
    }

    override suspend fun setState(value: Boolean) {
        hardware.geiger.setEyeConfiguration(value)
    }

    override fun createStateChangedEvent(enabled: Boolean) = MagicEyeStateChanged(clock.now(), enabled)

    override fun isToggleStateCommand(command: Command) = command is MagicEyeToggleStateCommand

    override fun isChangeStateCommand(command: Command) = command is MagicEyeChangeStateCommand

    override fun isPublishStateCommand(command: Command) = command is MagicEyePublishStateCommand
}

@OptIn(ExperimentalTime::class)
class BacklightDemon(
    private val hardware: Hardware,
    snapshotBus: DataSnapshotBus,
    commandBus: CommandBus,
    private val clock: Clock = Clock.System,
) : MqttExportableSwitchDemon(logger, snapshotBus, commandBus) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override val humanReadableName = "backlight"

    // we use relay 2
    override suspend fun retrieveState(): Boolean = hardware.clockDisplay.retrieveRelaysState().second
    override suspend fun setState(value: Boolean) = hardware.clockDisplay.setRelays(false, value)

    override fun createStateChangedEvent(enabled: Boolean) = BacklightStateChanged(clock.now(), enabled)

    override fun isToggleStateCommand(command: Command) = command is BacklightToggleStateCommand

    override fun isChangeStateCommand(command: Command) = command is BacklightChangeStateCommand

    override fun isPublishStateCommand(command: Command) = command is BacklightPublishStateCommand
}
