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


abstract class AbstractHardwareBooleanDemon(
    private val logger: KLogger,
    private val snapshotBus: DataSnapshotBus,
    private val commandBus: CommandBus,
) : Demon {

    private val stateFlow = lazy {
        MutableStateFlow(runBlocking {
            checkIfHardwareBooleanIsTrue()
        })
    }

    protected abstract val humanReadableName: String

    protected abstract suspend fun checkIfHardwareBooleanIsTrue(): Boolean

    protected abstract suspend fun setHardwareBoolean(enabled: Boolean)

    protected abstract fun createStateChangedEvent(enabled: Boolean): StateChanged

    protected abstract fun isChangeStateCommand(command: Command): Boolean

    protected abstract fun isPublishStateCommand(command: Command): Boolean

    override fun createJobs(scope: CoroutineScope): List<Job> = listOf(scope.launch {

        queryHardwareBoolean()

        commandBus.commands.collect { command ->
            if (isChangeStateCommand(command)) {
                command as ChangeStateCommand
                setHardwareBoolean(command.enabled)
                queryHardwareBoolean()
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
            queryHardwareBoolean()
            delay(800.milliseconds)
        }
    })

    private suspend fun queryHardwareBoolean() {
        val enabled = try {
            checkIfHardwareBooleanIsTrue()
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
) : AbstractHardwareBooleanDemon(logger, snapshotBus, commandBus) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override val humanReadableName = "magic eye"

    override suspend fun checkIfHardwareBooleanIsTrue() = when (hardware.geiger.getDeviceState().eyeState) {
        EyeInverterState.DISABLED -> false
        else -> true
    }

    override suspend fun setHardwareBoolean(enabled: Boolean) {
        hardware.geiger.setEyeConfiguration(enabled)
    }

    override fun createStateChangedEvent(enabled: Boolean) = MagicEyeStateChanged(clock.now(), enabled)

    override fun isChangeStateCommand(command: Command) = command is MagicEyeChangeStateCommand

    override fun isPublishStateCommand(command: Command) = command is MagicEyePublishStateCommand
}

@OptIn(ExperimentalTime::class)
class BacklightDemon(
    private val hardware: Hardware,
    snapshotBus: DataSnapshotBus,
    commandBus: CommandBus,
    private val clock: Clock = Clock.System,
) : AbstractHardwareBooleanDemon(logger, snapshotBus, commandBus) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    override val humanReadableName = "backlight"

    // we use relay 2
    override suspend fun checkIfHardwareBooleanIsTrue(): Boolean = hardware.clockDisplay.retrieveRelaysState().second
    override suspend fun setHardwareBoolean(enabled: Boolean) = hardware.clockDisplay.setRelays(false, enabled)

    override fun createStateChangedEvent(enabled: Boolean) = BacklightStateChanged(clock.now(), enabled)

    override fun isChangeStateCommand(command: Command) = command is BacklightChangeStateCommand

    override fun isPublishStateCommand(command: Command) = command is BacklightPublishStateCommand
}
