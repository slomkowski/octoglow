package eu.slomkowski.octoglow.octoglowd.mqtt

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import de.kempmobil.ktor.mqtt.QoS
import de.kempmobil.ktor.mqtt.buildFilterList
import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.HistoricalValueType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.ClassDiscriminatorMode
import java.nio.charset.StandardCharsets
import java.util.*

class MqttEmiter(
    private val config: Config,
    private val workerScope: CoroutineScope,
) : AutoCloseable {

    companion object {
        private val logger = KotlinLogging.logger { }

        private val mqttJsonSerializer = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = false
            classDiscriminatorMode = ClassDiscriminatorMode.NONE
        }
    }

    private val client = MqttClient(config.mqtt.host, config.mqtt.port) {
        // random string to prevent two clients with the same clientId
        clientId = "octoglowd-" + UUID.randomUUID().toString().replace("-", "").take(8)
        username = config.mqtt.username
        password = config.mqtt.password
        keepAliveSeconds = 30u
    }

    init {
        // todo rewrite to allow situation of unconnected client, publish status in a StateChannel
        workerScope.launch {
            client.publishedPackets.collect { publish ->
                try {
                    when (publish.topic.name) {
                        magicEyeSwitchSetTopic -> {
                            val payloadStr = publish.payload.decodeToString(StandardCharsets.UTF_8).uppercase().trim()
                            val state = SwitchStateEnum.valueOf(payloadStr)

                            client.publish(PublishRequest(magicEyeSwitchTopic) {
                                payload(mqttJsonSerializer.encodeToString(SwitchState(state)))
                            })
                        }

                        else -> logger.warn { "Received message with unexpected topic: $publish." }
                    }
                } catch (e: SerializationException) {
                    logger.error(e) { "Error while decoding $publish" }
                }
            }
        }

        runBlocking {
            client.connect().onSuccess { connack ->
                if (connack.isSuccess) {
                    logger.info { "Successfully connected to ${config.mqtt.host}:${config.mqtt.port}" }
                    sendHomeassistantDiscoveryMessage()

                    client.subscribe(buildFilterList {
                        //todo subcribe to homeassistant/status, send discovery when it becomes 'online'
                        add(magicEyeSwitchSetTopic)
                    })
                } else {
                    error("Failed to connect to ${config.mqtt.host}:${config.mqtt.port}")
                }
            }.onFailure {
                throw it
            }
        }
    }

    // publikacja: temperatury, geiger itd
    // przyjmowanie: obsługa gałki: przyciski
    // magic eye: status
    // custom text
    // notification

    suspend fun publishMeasurement(sensorKey: HistoricalValueType, value: Double): Boolean {
        val sensor = availableSensors[sensorKey] ?: return false

        val payloadMsg = sensor.payloadFunc.invoke(value)
        val topic = sensor.topic

        logger.debug { "Publishing value $value ${sensor.unitOfMeasurement} to $topic." }
        client.publish(PublishRequest(topic) {
            desiredQoS = QoS.AT_LEAST_ONCE
            payload(mqttJsonSerializer.encodeToString(payloadMsg))
        })

        return true
    }

    suspend fun sendHomeassistantDiscoveryMessage() {
        val payload = createDiscoveryMessageDto()
        val topic = "${config.mqtt.homeassistantDiscoveryPrefix}/device/$DEVICE_ID/config"
        logger.info { "Sending Homeassistant discovery message to $topic" }
        client.publish(PublishRequest(topic) {
            desiredQoS = QoS.AT_LEAST_ONCE
            isRetainMessage = true
            payload(mqttJsonSerializer.encodeToString(payload))
        })
    }

    override fun close() {
        client.close()
    }
}