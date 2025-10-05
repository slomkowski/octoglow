package eu.slomkowski.octoglow.octoglowd.demon


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.EyeInverterState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class MagicEyeDemon(
    private val hardware: Hardware,
    private val snapshotBus: DataSnapshotBus,
    private val commandBus: CommandBus,
    private val clock: Clock = Clock.System,
) : Demon {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val eyeEnabledFlow: MutableStateFlow<Boolean>

    init {
        eyeEnabledFlow = MutableStateFlow(runBlocking {
            checkIfEyeIsEnabled()
        })
    }

    override fun createJobs(scope: CoroutineScope): List<Job> = listOf(scope.launch {

        queryEyeState()

        commandBus.commands.collect { command ->
            when (command) {
                is MagicEyeChangeStateCommand -> {
                    hardware.geiger.setEyeConfiguration(command.enabled)
                    queryEyeState()
                }

                is MagicEyePublishStateCommand -> {
                    queryEyeState()
                }
            }
        }
    }, scope.launch {
        eyeEnabledFlow.collect { eyeEnabled ->
            logger.info { "Eye enabled: $eyeEnabled, publishing its state." }
            snapshotBus.publish(MagicEyeStateChanged(clock.now(), eyeEnabled))
        }
    }, scope.launch {
        while (isActive) {
            queryEyeState()
            delay(800.milliseconds)
        }
    })

    private suspend fun queryEyeState() {
        val eyeEnabled = try {
            checkIfEyeIsEnabled()
        } catch (e: Exception) {
            logger.error(e) { "Error checking magic eye state. Assuming it is disabled" }
            false
        }

        eyeEnabledFlow.emit(eyeEnabled)
    }

    private suspend fun checkIfEyeIsEnabled(): Boolean = when (hardware.geiger.getDeviceState().eyeState) {
        EyeInverterState.DISABLED -> false
        else -> true
    }
}
