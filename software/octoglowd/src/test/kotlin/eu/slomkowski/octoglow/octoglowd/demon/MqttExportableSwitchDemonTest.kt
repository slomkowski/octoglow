package eu.slomkowski.octoglow.octoglowd.demon

import eu.slomkowski.octoglow.octoglowd.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


class MqttExportableSwitchDemonTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private lateinit var demon: MqttExportableSwitchDemon
    private val snapshotBus = mockk<DataSnapshotBus>(relaxed = true)
    private val commandBus = mockk<CommandBus>(relaxed = true)

    @BeforeEach
    fun setUp() {
        demon = object : MqttExportableSwitchDemon(logger, snapshotBus, commandBus) {
            override val humanReadableName = "test demon"

            override suspend fun retrieveState() = true

            override suspend fun setState(value: Boolean) {}

            override fun createStateChangedEvent(enabled: Boolean) = mockk<StateChanged>()
            override fun isToggleStateCommand(command: Command) = command is ToggleStateCommand

            override fun isChangeStateCommand(command: Command) = command is ChangeStateCommand

            override fun isPublishStateCommand(command: Command) = command is PublishStateCommand
        }
    }

    @Test
    fun `retrieveState returns expected state`() = runTest {
        val state = demon.retrieveState()
        assertThat(state).isTrue()
    }

    @Test
    fun `setState updates state correctly`() = runTest {
        demon.setState(true)
        verify { logger.info(any<String>()) }
    }

    @Test
    fun `createStateChangedEvent returns correct event`() {
        val event = demon.createStateChangedEvent(true)
        assertThat(event).isNotNull
    }

    @Test
    fun `isChangeStateCommand identifies correct command`() {
        val command = mockk<ChangeStateCommand>()
        val result = demon.isChangeStateCommand(command)
        assertThat(result).isTrue()
    }

    @Test
    fun `isPublishStateCommand identifies correct command`() {
        val command = mockk<PublishStateCommand>()
        val result = demon.isPublishStateCommand(command)
        assertThat(result).isTrue()
    }

    @Test
    fun `queryAndEmitState handles exception and publishes false`() = runTest {
        demon = object : MqttExportableSwitchDemon(logger, snapshotBus, commandBus) {
            override val humanReadableName = "test demon"
            override suspend fun retrieveState(): Boolean {
                throw Exception("Simulated error")
            }

            override suspend fun setState(value: Boolean) {}

            override fun isToggleStateCommand(command: Command) = false

            override fun createStateChangedEvent(enabled: Boolean) = mockk<StateChanged>()

            override fun isChangeStateCommand(command: Command) = false

            override fun isPublishStateCommand(command: Command) = false
        }

        demon.queryAndEmitState()
        coVerify { snapshotBus.publish(any()) }
    }
}