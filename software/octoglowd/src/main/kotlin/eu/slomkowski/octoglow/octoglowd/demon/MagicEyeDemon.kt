package eu.slomkowski.octoglow.octoglowd.demon


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.EyeInverterState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Clock
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

    override fun createJobs(scope: CoroutineScope): List<Job> = listOf(scope.launch {

        queryAndPublishEyeState()

        commandBus.commands.collect { command ->
            when (command) {
                is MagicEyeChangeStateCommand -> {
                    hardware.geiger.setEyeConfiguration(command.enabled)
                    snapshotBus.publish(MagicEyeStateChanged(clock.now(), command.enabled))
                }

                is MagicEyePublishStateCommand -> {
                    queryAndPublishEyeState()
                }
            }
        }
    })

    private suspend fun queryAndPublishEyeState() {
        val eyeEnabled = when (hardware.geiger.getDeviceState().eyeState) {
            EyeInverterState.DISABLED -> false
            else -> true
        }
        logger.info { "Eye enabled: $eyeEnabled, publishing its state." }
        snapshotBus.publish(MagicEyeStateChanged(clock.now(), eyeEnabled))
    }
}
