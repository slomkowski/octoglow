package eu.slomkowski.octoglow.octoglowd.mqtt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
            @SerialName("unit_of_measurement") val unitOfMeasurement: String? = null,
            @SerialName("value_template") val valueTemplate: String,
            @SerialName("state_topic") val stateTopic: String,
            @SerialName("unique_id") val uniqueId: String,
            val icon: String? = null,
            @SerialName("state_class") val stateClass: String? = null,
        ) : Component("sensor")

        @Serializable
        data class Switch(
            val name: String,
            @SerialName("state_topic") val stateTopic: String,
            @SerialName("command_topic") val commandTopic: String,
            @SerialName("value_template") val valueTemplate: String,
            @SerialName("unique_id") val uniqueId: String,
        ) : Component("switch")

        @Serializable
        data class Button(
            val name: String,
            @SerialName("command_topic") val commandTopic: String,
            @SerialName("payload_press") val payloadPress: String,
            @SerialName("unique_id") val uniqueId: String,
        ) : Component("button")
    }
}

@Serializable
data class SwitchState(
    val state: SwitchStateEnum
)

typealias PayloadFunc = (Double) -> SensorPayload

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

    @Serializable
    data class RawValuePayload(
        @SerialName("raw_value")
        val rawValue: Double,
    ) : SensorPayload()
}

@Serializable
enum class SwitchStateEnum(val active: Boolean) {
    OFF(false),
    ON(true),
}
