@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.mqtt

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import de.kempmobil.ktor.mqtt.QoS
import de.kempmobil.ktor.mqtt.buildFilterList
import de.kempmobil.ktor.mqtt.packet.Publish
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.demon.Demon
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.ClassDiscriminatorMode
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class MqttDemon(
    private val config: Config,
    private val snapshotBus: DataSnapshotBus,
    private val commandBus: CommandBus,
) : Demon {

    companion object {
        private val logger = KotlinLogging.logger { }

        private val mqttJsonSerializer = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = false
            classDiscriminatorMode = ClassDiscriminatorMode.NONE
        }

        private fun Publish.payloadAsString(): String = this.payload.decodeToString(StandardCharsets.UTF_8)

        private val connectRetryInterval = 10.seconds
    }

    private val homeassistantStatusTopic = "${config.mqtt.homeassistantDiscoveryPrefix}/status"

    private val mqttServerString = "${config.mqtt.host}:${config.mqtt.port}"

    private val messagesToPublish = Channel<PublishRequest>(
        capacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    @Volatile
    private var senderOrTryToConnectJob: Job? = null

    private val mqttClient = MqttClient(config.mqtt.host, config.mqtt.port) {
        // random string to prevent two clients with the same clientId
        clientId = "octoglowd-" + UUID.randomUUID().toString().replace("-", "").take(8)
        username = config.mqtt.username
        password = config.mqtt.password
        keepAliveSeconds = 15u

        willMessage(availabilityTopic) {
            payload("offline")
        }
    }

    override fun close(scope: CoroutineScope) {
        scope.launch {
            senderOrTryToConnectJob?.cancelAndJoin()
        }
        mqttClient.close()
    }

    private suspend fun schedulePublish(topic: String, payload: String) {
        messagesToPublish.send(PublishRequest(topic) {
            payload(payload)
            desiredQoS = QoS.AT_LEAST_ONCE
        })
    }

    private suspend fun actionsOnConnect() {
        mqttClient.subscribe(buildFilterList {
            add(magicEyeSwitchSetTopic)
            add(dialButtonTopic)
            add(homeassistantStatusTopic)
        })

        sendHomeassistantDiscoveryMessage()

        mqttClient.publish(PublishRequest(availabilityTopic) {
            payload("online")
            desiredQoS = QoS.AT_LEAST_ONCE
        })
    }

    private suspend fun handlePublishedPacket(publish: Publish): Unit = coroutineScope {
        try {
            when (publish.topic.name) {
                magicEyeSwitchSetTopic -> {
                    val payloadStr = publish.payloadAsString().uppercase().trim()
                    val state = SwitchStateEnum.valueOf(payloadStr)
                    logger.info { "Magic eye set to '$state' from MQTT." }
                    commandBus.publish(MagicEyeCommand(state.active))
                }

                dialButtonTopic -> {
                    val payloadStr = publish.payloadAsString().lowercase().trim()
                    when (payloadStr) {
                        dialCommandCw -> DialTurned(1)
                        dialCommandCcw -> DialTurned(-1)
                        dialCommandPress -> DialPressed
                        else -> null
                    }?.let { commandBus.publish(it) }
                }

                homeassistantStatusTopic -> {
                    if (publish.payloadAsString().equals("online", true)) {
                        launch {
                            delay(Random.nextLong(500, 3000)) // according to Homeassistant documentation
                            sendHomeassistantDiscoveryMessage()
                        }
                    }
                }

                else -> logger.warn { "Received message with unexpected topic: $publish." }
            }
        } catch (e: SerializationException) {
            logger.error(e) { "Error while decoding $publish" }
        }
    }

    private suspend fun publishDataSample(sensorKey: DataSampleType, value: Double) = coroutineScope {
        val sensor = availableSensors[sensorKey] ?: return@coroutineScope false

        val payloadMsg = sensor.payloadFunc.invoke(value)
        val topic = sensor.topic

        launch {
            logger.info { "Adding data sample ${"%.4f".format(value)} ${sensor.unitOfMeasurement} to queue as $topic." }
            schedulePublish(sensor.topic, mqttJsonSerializer.encodeToString(payloadMsg))
        }
    }

    private suspend fun sendHomeassistantDiscoveryMessage(): Unit = coroutineScope {
        val payload = createDiscoveryMessageDto()
        val topic = "${config.mqtt.homeassistantDiscoveryPrefix}/device/$DEVICE_ID/config"
        logger.info { "Sending Homeassistant discovery message to $topic." }
        mqttClient.publish(PublishRequest(topic) {
            desiredQoS = QoS.AT_LEAST_ONCE
            isRetainMessage = true
            payload(mqttJsonSerializer.encodeToString(payload))
        })
    }

    private fun createConnectionStateListener(workerScope: CoroutineScope): Job = workerScope.launch {
        mqttClient.connectionState.collect { state ->
            senderOrTryToConnectJob?.cancelAndJoin()

            senderOrTryToConnectJob = if (state.isConnected) {
                logger.info { "Connected to MQTT server $mqttServerString." }
                actionsOnConnect()

                // this launches the sender job
                workerScope.launch {
                    while (isActive) {
                        val publishRequest = messagesToPublish.receive()
                        logger.debug { "Publishing message to ${publishRequest.topic.name}." }
                        mqttClient.publish(publishRequest)
                    }
                }
            } else {
                logger.warn { "Lost connection to MQTT server $mqttServerString." }

                // this launches the try-to-connect job
                workerScope.launch {
                    while (isActive) {
                        mqttClient.connect().onSuccess { connack ->
                            if (connack.isSuccess) {
                                break
                            } else {
                                logger.warn { "Failed to connect to $mqttServerString: $connack, retrying in $connectRetryInterval." }
                            }
                        }.onFailure { exp ->
                            val rootCause = generateSequence(exp) { it.cause }.last()
                            logger.warn { "Failed to connect to $mqttServerString: ${rootCause.message}, retrying in $connectRetryInterval." }
                        }
                        delay(connectRetryInterval)
                    }
                }
            }

            workerScope.launch { snapshotBus.publish(MqttConnectionChanged(Clock.System.now(), state.isConnected)) }
        }
    }

    private fun createSnapshotBusListener(workerScope: CoroutineScope): Job = workerScope.launch {
        snapshotBus.snapshots.collect { cmd ->
            when (cmd) {
                is DataSnapshot -> cmd.values.forEach { savableData ->
                    launch {
                        savableData.value.onSuccess { v -> publishDataSample(savableData.type, v) }
                    }
                }

                is MagicEyeStateChanged -> {
                    val state = cmd.enabled.toSwitchStateEnum()
                    logger.debug { "Publishing magic eye state $state." }
                    launch {
                        schedulePublish(magicEyeSwitchTopic, state.name)
                    }
                }
            }
        }
    }

    private fun createIncomingPacketsListener(workerScope: CoroutineScope): Job = workerScope.launch {
        mqttClient.publishedPackets.collect { publish -> handlePublishedPacket(publish) }
    }

    override fun createJobs(scope: CoroutineScope): List<Job> = listOf(
        createIncomingPacketsListener(scope),
        createSnapshotBusListener(scope),
        createConnectionStateListener(scope),
    )
}