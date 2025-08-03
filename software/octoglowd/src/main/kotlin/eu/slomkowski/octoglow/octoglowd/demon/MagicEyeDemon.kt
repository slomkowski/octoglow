package eu.slomkowski.octoglow.octoglowd.demon


import eu.slomkowski.octoglow.octoglowd.CommandBus
import eu.slomkowski.octoglow.octoglowd.DataSnapshotBus
import eu.slomkowski.octoglow.octoglowd.MagicEyeCommand
import eu.slomkowski.octoglow.octoglowd.MagicEyeStateChanged
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
        val eyeEnabled = when (hardware.geiger.getDeviceState().eyeState) {
            EyeInverterState.DISABLED -> false
            else -> true
        }
        logger.info { "Eye enabled: $eyeEnabled, publishing its state." }
        snapshotBus.publish(MagicEyeStateChanged(clock.now(), eyeEnabled))

        commandBus.commands.collect { command ->
            if (command is MagicEyeCommand) {
                hardware.geiger.setEyeConfiguration(command.enabled)
                snapshotBus.publish(MagicEyeStateChanged(clock.now(), command.enabled))
            }
        }
    })
}
