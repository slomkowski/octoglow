package eu.slomkowski.octoglow.octoglowd.demon


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.EyeInverterState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


abstract class MqttExportableSwitchDemon(
    private val logger: KLogger,
    private val snapshotBus: DataSnapshotBus,
    private val commandBus: CommandBus,
) : Demon {

    companion object {
        /**
         * How often the device is asked for its actual state, to notice changes we did not make
         * ourselves (a board reset, for instance). Each query is an I2C transaction competing with
         * the front display, so this only has to be fast enough for Home Assistant to look live.
         */
        private val statePollInterval = 5.seconds
    }

    // not a lazy { runBlocking { ... } }: that was first forced from a Dispatchers.Default worker and
    // parked it across an I2C round trip. null means "not read from the device yet", so nothing is
    // published until createJobs' first queryAndEmitState() has answered.
    private val stateFlow = MutableStateFlow<Boolean?>(null)

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
                setState(stateFlow.value != true)
            } else if (isChangeStateCommand(command)) {
                command as ChangeStateCommand
                setState(command.enabled)
                queryAndEmitState()
            } else if (isPublishStateCommand(command)) {
                command as PublishStateCommand
                logger.info { "Publishing $humanReadableName state on request." }
                stateFlow.value?.let { snapshotBus.publish(createStateChangedEvent(it)) }
            }
        }
    }, scope.launch {
        stateFlow.filterNotNull().collect { enabled ->
            logger.info { "${humanReadableName.replaceFirstChar { it.uppercaseChar() }} enabled: $enabled, publishing its state." }
            snapshotBus.publish(createStateChangedEvent(enabled))
        }
    }, scope.launch {
        while (isActive) {
            queryAndEmitState()
            delay(statePollInterval)
        }
    })

    suspend fun queryAndEmitState() {
        val enabled = try {
            retrieveState()
        } catch (e: Exception) {
            logger.error(e) { "Error checking $humanReadableName state. Assuming it is disabled" }
            false
        }

        stateFlow.emit(enabled)
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
