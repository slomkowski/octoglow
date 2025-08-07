@file:OptIn(ExperimentalTime::class, ExperimentalAtomicApi::class)

package eu.slomkowski.octoglow.octoglowd

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import eu.slomkowski.octoglow.octoglowd.mqtt.MqttDemon
import eu.slomkowski.octoglow.octoglowd.mqtt.magicEyeSwitchSetTopic
import io.github.oshai.kotlinlogging.KotlinLogging
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.take
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


class MqttDemonTest {

    companion object {
        private val logger = KotlinLogging.logger { }
        private val mqttPort = Random.nextInt(32500, 33500)

        val config = testConfig.copy(
            mqtt = ConfMqttInfo(
                enabled = true,
                host = "localhost",
                port = mqttPort,
            )
        )
    }

    private lateinit var mqttServer: Server

    private fun startMqttBroker() {
        val config = MemoryConfig(Properties().apply {
            setProperty("port", mqttPort.toString())
            setProperty("host", "localhost")
            setProperty("allow_anonymous", "true")
        })

        mqttServer = Server()
        mqttServer.startServer(config)
    }

    private fun stopMqttBroker() {
        mqttServer.stopServer()
    }

    @Test
    fun testInterpretCommands(): Unit = runBlocking(Dispatchers.Default) {
        val snapshotBus = DataSnapshotBus()
        val commandBus = CommandBus()

        startMqttBroker()

        val mqttEmitter = MqttDemon(
            config,
            snapshotBus,
            commandBus,
        )

        val client2 = MqttClient(config.mqtt.host, config.mqtt.port) {
            clientId = "unit-test"
        }

        withTimeoutOrNull(30.seconds) {
            assertThat(client2.connect().getOrThrow()).isNotNull()

            launch {
                delay(4.seconds)
                client2.publish(PublishRequest(magicEyeSwitchSetTopic) {
                    payload("ON")
                })
                delay(100.milliseconds)
                commandBus.commands.take(1).collect { cmd ->
                    cmd as MagicEyeCommand
                    assertThat(cmd.enabled).isTrue()
                }
                client2.publish(PublishRequest(magicEyeSwitchSetTopic) {
                    payload("OFF")
                })
                delay(100.milliseconds)
                commandBus.commands.drop(1).take(1).collect { cmd ->
                    cmd as MagicEyeCommand
                    assertThat(cmd.enabled).isFalse()
                }
            }

            mqttEmitter.createJobs(this)
        }

        client2.close()
        mqttEmitter.close(this)

        stopMqttBroker()
    }

    @Test
    fun testConnectionState(): Unit = runBlocking(Dispatchers.Default) {

        val snapshotBus = DataSnapshotBus()
        val commandBus = CommandBus()

        val isCurrentlyConnected = java.util.concurrent.atomic.AtomicBoolean(false)

        assertThat(isCurrentlyConnected.get()).isFalse()

        launch(Dispatchers.IO) {
            delay(5.seconds)
            startMqttBroker()
            delay(15.seconds)
            assertThat(isCurrentlyConnected.get()).isTrue()
            delay(20.seconds)
            stopMqttBroker()
            delay(10.seconds)
            assertThat(isCurrentlyConnected.get()).isFalse()
        }

        val mqttEmitter = MqttDemon(
            config,
            snapshotBus,
            commandBus,
        )

        withTimeoutOrNull(1.minutes) {
            launch {
                listOf(true, false, true, false).forEach {
                    snapshotBus.publish(
                        MagicEyeStateChanged(Clock.System.now(), it)
                    )
                }

                while (isActive) {
                    snapshotBus.publish(
                        StandardDataSnapshot(
                            Clock.System.now(),
                            5.minutes,
                            listOf(StandardDataSample(IndoorTemperature, Result.success(Random.nextDouble(20.0, 30.0))))
                        )
                    )
                    delay(3.seconds)
                }
            }

            launch {
                snapshotBus.snapshots.collect { snapshot ->
                    logger.info { "Got snapshot: $snapshot" }
                    (snapshot as? MqttConnectionChanged)?.let { connState ->
                        isCurrentlyConnected.set(connState.connected)
                    }
                }
            }

            mqttEmitter.createJobs(this)
        }

        mqttEmitter.close(this)
    }
}
