package eu.slomkowski.octoglow.octoglowd

import de.kempmobil.ktor.mqtt.MqttClient
import de.kempmobil.ktor.mqtt.PublishRequest
import de.kempmobil.ktor.mqtt.buildFilterList
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.decodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.ClassDiscriminatorMode
import java.nio.charset.StandardCharsets

typealias PayloadFunc = (Double) -> MqttEmiter.SensorPayload

class MqttEmiter(
    private val config: Config,
    private val workerScope: CoroutineScope,
) : AutoCloseable {

    companion object {
        private val logger = KotlinLogging.logger { }

        private const val deviceId = "octoglow"

        const val magicEyeIdentifier = "magic_eye"
        const val magicEyeSwitchSetTopic = "octoglow/switch/$magicEyeIdentifier/set"
        const val magicEyeSwitchTopic = "octoglow/switch/$magicEyeIdentifier"

        private val mqttJsonSerializer = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = false
            classDiscriminatorMode = ClassDiscriminatorMode.NONE
        }

        private val availableSensors = listOf(
            HaTemperature(IndoorTemperature, "Temperatura wewnątrz"),
            HaTemperature(OutdoorTemperature, "Temperatura na zewnątrz"),
            HaHumidity(IndoorHumidity, "Wilgotność wewnątrz"),
            HaHumidity(OutdoorHumidity, "Wilgotność na zewnątrz"),
            SendableToHomeassistant(
                MSLPressure,
                "Ciśnienie atmosferyczne",
                "atmospheric_pressure",
                "hPa",
                "{{ value_json.pressure | round(0) }}",
                null,
            ) { SensorPayload.PressurePayload(it) },
            SendableToHomeassistant(
                RadioactivityUSVH,
                "Promieniowanie γ",
                null,
                "µSv/h",
                "{{ value_json.radioactivity | round(2) }}",
                "mdi:radioactive",
            ) { SensorPayload.Radioactivity(it) },
            SendableToHomeassistant(
                IndoorCo2,
                "Stężenie CO2",
                "carbon_dioxide",
                "ppm",
                "{{ value_json.concentration | round(0) }}",
                null,
            ) { SensorPayload.ConcentrationPayload(it) }
        ).associateBy { it.type }

        fun createDiscoveryMessageDto(): DeviceConfig {
            val sensors = availableSensors.values.associate {
                it.haIdentifier to DeviceConfig.Component.Sensor(
                    name = it.humanReadableName,
                    deviceClass = it.deviceClass,
                    unitOfMeasurement = it.unitOfMeasurement,
                    valueTemplate = it.valueTemplate,
                    stateTopic = it.topic,
                    uniqueId = "${deviceId}_${it.haIdentifier}",
                    icon = it.icon,
                )
            }

            return DeviceConfig(
                device = DeviceConfig.DeviceInfo(
                    identifiers = listOf("octoglow"),
                    name = "Octoglow",
                    manufacturer = "Michał Słomkowski",
                    model = "Octoglow VFD Fallout-inspired display",
                ),
                origin = DeviceConfig.DeviceDetails(
                    name = "Octoglow",
                    url = "https://slomkowski.eu/projects/octoglow-vfd-fallout-inspired-display/"
                ),
                components = sensors.plus(
                    magicEyeIdentifier to DeviceConfig.Component.Switch(
                        name = "Oko magiczne",
                        stateTopic = magicEyeSwitchTopic,
                        commandTopic = magicEyeSwitchSetTopic,
                        "{{ value_json.state }}",
                        uniqueId = "${deviceId}_${magicEyeIdentifier}",
                    )
                ),
            )
        }
    }


    @Serializable
    enum class SwitchStateEnum(val active: Boolean) {
        OFF(false),
        ON(true),
    }

    private val client = MqttClient(config.mqtt.host, config.mqtt.port) {
        clientId = "octoglowd" // todo better clientId
        username = config.mqtt.username
        password = config.mqtt.password
    }

    init {
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
                    writeDiscoveryMessage()

                    client.subscribe(buildFilterList {
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

    @Serializable
    data class SwitchState(
        val state: SwitchStateEnum
    )

    // todo remove 'type' from mqtt json
    @Serializable
    sealed class SensorPayload {

        @Serializable
        data class Temperature(
            val temperature: Double,
        ) : SensorPayload()

        @Serializable
        data class Radioactivity(
            val radioactivity: Double,
        ) : SensorPayload()

        @Serializable
        data class HumidityPayload(
            val humidity: Double,
        ) : SensorPayload()

        @Serializable
        data class PressurePayload(
            val pressure: Double,
        ) : SensorPayload()

        @Serializable
        data class ConcentrationPayload(
            val concentration: Double,
        ) : SensorPayload()
    }

    class HaTemperature(
        type: HistoricalValueType,
        humanReadableName: String,
    ) : SendableToHomeassistant(
        type,
        humanReadableName,
        "temperature",
        "°C",
        "{{ value_json.temperature  | round(1) }}",
        null,
        { SensorPayload.Temperature(it) },
    )

    class HaHumidity(
        type: HistoricalValueType,
        humanReadableName: String,
    ) : SendableToHomeassistant(
        type,
        humanReadableName,
        "humidity",
        "%",
        "{{ value_json.humidity | round(0) }}",
        null,
        { SensorPayload.HumidityPayload(it) },
    )

    open class SendableToHomeassistant(
        val type: HistoricalValueType,
        val humanReadableName: String,
        val deviceClass: String?,
        val unitOfMeasurement: String,
        val valueTemplate: String,
        val icon: String?,
        val payloadFunc: PayloadFunc,
    ) {
        val haIdentifier: String
            get() = type.databaseSymbol.lowercase()

        val topic = "octoglow/sensor/${haIdentifier}"
    }


    suspend fun publishMeasurement(sensorKey: HistoricalValueType, value: Double): Boolean {
        val sensor = availableSensors[sensorKey] ?: return false

        val payloadMsg = sensor.payloadFunc.invoke(value)
        val topic = sensor.topic

        logger.debug { "Publishing value $value ${sensor.unitOfMeasurement} to $topic." }
        client.publish(PublishRequest(topic) {
            payload(mqttJsonSerializer.encodeToString(payloadMsg))
        })

        return true
    }

    @Serializable
    data class DeviceConfig(
        val device: DeviceInfo,
        val origin: DeviceDetails,
        val components: Map<String, Component>,
    ) {
        @Serializable
        data class DeviceInfo(
            val identifiers: List<String>,
            val name: String,
            val model: String,
            val manufacturer: String,
        )

        @Serializable
        data class DeviceDetails(
            val name: String,
            val url: String
        )

        @Serializable
        sealed class Component(
            val platform: String,
        ) {

            @Serializable
            data class Sensor(
                val name: String,
                @SerialName("device_class") val deviceClass: String? = null,
                @SerialName("unit_of_measurement") val unitOfMeasurement: String,
                @SerialName("value_template") val valueTemplate: String,
                @SerialName("state_topic") val stateTopic: String,
                @SerialName("unique_id") val uniqueId: String,
                val icon: String? = null,
            ) : Component("sensor")

            @Serializable
            data class Switch(
                val name: String,
                @SerialName("state_topic") val stateTopic: String,
                @SerialName("command_topic") val commandTopic: String,
                @SerialName("value_template") val valueTemplate: String,
                @SerialName("unique_id") val uniqueId: String,
            ) : Component("switch")
        }
    }


    suspend fun writeDiscoveryMessage() {
        val payload = createDiscoveryMessageDto()
        val topic = "${config.mqtt.homeassistantDiscoveryPrefix}/device/${deviceId}/config"
        logger.info { "Sending Homeassistant discovery message to $topic" }
        client.publish(PublishRequest(topic) {
            isRetainMessage = true
            payload(mqttJsonSerializer.encodeToString(payload))
        })
    }


    override fun close() {
        client.close()
    }
}