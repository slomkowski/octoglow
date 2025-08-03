package eu.slomkowski.octoglow.octoglowd.mqtt

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import de.kempmobil.ktor.mqtt.QoS
import de.kempmobil.ktor.mqtt.buildFilterList
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.demon.Demon
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.ClassDiscriminatorMode
import java.nio.charset.StandardCharsets
import java.util.*

class MqttEmiter(
    private val config: Config,
    private val workerScope: CoroutineScope,
    private val snapshotBus: DataSnapshotBus,
    private val commandBus: CommandBus,
) : AutoCloseable, Demon {

    companion object {
        private val logger = KotlinLogging.logger { }

        private val mqttJsonSerializer = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = false
            classDiscriminatorMode = ClassDiscriminatorMode.NONE
        }
    }

    private val mqttClient = MqttClient(config.mqtt.host, config.mqtt.port) {
        // random string to prevent two clients with the same clientId
        clientId = "octoglowd-" + UUID.randomUUID().toString().replace("-", "").take(8)
        username = config.mqtt.username
        password = config.mqtt.password
        keepAliveSeconds = 15u
    }

    // todo obserwuje status connected. jeżeli nie jest connected, to co 30 sekund próbuje się połączyć
    // jeżeli  przejedzie connected, to subskrypcje i możliwość wysyłania

    private val mqttThreadContext = newSingleThreadContext("Mqtt")
    private val mqttWorkerScope = CoroutineScope(SupervisorJob() + mqttThreadContext)

    init {
        // todo rewrite to allow situation of unconnected client, publish status in a StateChannel
        mqttWorkerScope.launch {
            mqttClient.publishedPackets.collect { publish ->
                try {
                    when (publish.topic.name) {
                        magicEyeSwitchSetTopic -> {
                            val payloadStr = publish.payload.decodeToString(StandardCharsets.UTF_8).uppercase().trim()
                            val state = SwitchStateEnum.valueOf(payloadStr)
                            logger.info { "Set magic eye from MQTT: $state." }
                            commandBus.publish(MagicEyeCommand(state.active))
                        }

                        dialButtonTopic -> {
                            val payloadStr = publish.payload.decodeToString(StandardCharsets.UTF_8).lowercase().trim()
                            when (payloadStr) {
                                dialCommandCw -> DialTurned(1)
                                dialCommandCcw -> DialTurned(-1)
                                dialCommandPress -> DialPressed
                                else -> null
                            }?.let { commandBus.publish(it) }
                        }

                        else -> logger.warn { "Received message with unexpected topic: $publish." }
                    }
                } catch (e: SerializationException) {
                    logger.error(e) { "Error while decoding $publish" }
                }
            }
        }

        // todo przerobić na ponawianie
        mqttWorkerScope.launch {
            mqttClient.connect().onSuccess { connack ->
                if (connack.isSuccess) {
                    logger.info { "Successfully connected to ${config.mqtt.host}:${config.mqtt.port}." }
                    sendHomeassistantDiscoveryMessage()

                    mqttClient.subscribe(buildFilterList {
                        //todo subcribe to homeassistant/status, send discovery when it becomes 'online'
                        add(magicEyeSwitchSetTopic)
                        add(dialButtonTopic)
                    })
                } else {
                    error("Failed to connect to ${config.mqtt.host}:${config.mqtt.port}")
                }
            }.onFailure {
                throw it
            }
        }
    }


    // todo: custom text
    // notification

    private fun publishMeasurement(sensorKey: DataSampleType, value: Double): Boolean {
        val sensor = availableSensors[sensorKey] ?: return false

        val payloadMsg = sensor.payloadFunc.invoke(value)
        val topic = sensor.topic

        mqttWorkerScope.launch {
            logger.debug { "Publishing value $value ${sensor.unitOfMeasurement} to $topic." }
            mqttClient.publish(PublishRequest(topic) {
                desiredQoS = QoS.AT_LEAST_ONCE
                payload(mqttJsonSerializer.encodeToString(payloadMsg))
            })
        }

        return true
    }

    fun sendHomeassistantDiscoveryMessage() {
        val payload = createDiscoveryMessageDto()
        val topic = "${config.mqtt.homeassistantDiscoveryPrefix}/device/$DEVICE_ID/config"
        logger.info { "Sending Homeassistant discovery message to $topic" }
        mqttWorkerScope.launch {
            mqttClient.publish(PublishRequest(topic) {
                desiredQoS = QoS.AT_LEAST_ONCE
                isRetainMessage = true
                payload(mqttJsonSerializer.encodeToString(payload))
            })
        }
    }

    override fun close() {
        mqttClient.close()
        mqttWorkerScope.cancel()
        mqttThreadContext.close()
    }

    override fun createJobs(scope: CoroutineScope): List<Job> = listOf(
        scope.launch {
            snapshotBus.snapshots.collect { cmd ->
                when (cmd) {
                    is DataSnapshot -> cmd.values.forEach { savableData ->
                        launch {
                            savableData.value.onSuccess { v -> publishMeasurement(savableData.type, v) }
                        }
                    }

                    is MagicEyeStateChanged -> {
                        val state = cmd.enabled.toSwitchStateEnum()
                        logger.debug { "Publishing magic eye state $state." }
                        mqttWorkerScope.launch {
                            mqttClient.publish(PublishRequest(magicEyeSwitchTopic) {
                                payload(state.name)
                                desiredQoS = QoS.AT_LEAST_ONCE
                            })
                        }
                    }
                }
            }
        })
}