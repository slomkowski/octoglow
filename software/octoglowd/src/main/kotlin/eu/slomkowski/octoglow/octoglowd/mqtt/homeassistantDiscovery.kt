package eu.slomkowski.octoglow.octoglowd.mqtt

import eu.slomkowski.octoglow.octoglowd.*

const val DEVICE_ID = "octoglow"
const val magicEyeIdentifier = "magic_eye"
const val magicEyeSwitchSetTopic = "$DEVICE_ID/switch/$magicEyeIdentifier/set"
const val magicEyeSwitchTopic = "$DEVICE_ID/switch/$magicEyeIdentifier"

const val availabilityTopic = "$DEVICE_ID/status"

const val dialIdentifier = "dial"
const val dialButtonTopic = "$DEVICE_ID/button/$dialIdentifier/commands"

const val dialCommandCcw = "dial_ccw"
const val dialCommandCw = "dial_cw"
const val dialCommandPress = "dial_press"

class HaTemperature(
    type: DbDataSampleType, // todo przerobić na HaMeasurementType?
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
    type: DbDataSampleType,
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
    val type: DbDataSampleType,
    val humanReadableName: String,
    val deviceClass: String?,
    val unitOfMeasurement: String?,
    val valueTemplate: String,
    val icon: String?,
    val payloadFunc: PayloadFunc,
) {
    val haIdentifier: String
        get() = type.databaseSymbol.lowercase()

    val topic = "octoglow/sensor/${haIdentifier}"
}


val availableSensors = listOf(
    HaTemperature(IndoorTemperature, "Temperatura wewnątrz"),
    HaTemperature(OutdoorTemperature, "Temperatura na zewnątrz"),
    HaHumidity(IndoorHumidity, "Wilgotność wewnątrz"),
    HaHumidity(OutdoorHumidity, "Wilgotność na zewnątrz"),
    SendableToHomeassistant(
        MSLPressure,
        "Ciśnienie atmosferyczne",
        "atmospheric_pressure",
        "hPa",
        "{{ value_json.pressure | round(1) }}",
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
        "Stężenie CO₂",
        "carbon_dioxide",
        "ppm",
        "{{ value_json.concentration | round(0) }}",
        null,
    ) { SensorPayload.ConcentrationPayload(it) },
    SendableToHomeassistant(
        LightSensorValue,
        "Czujnik światła",
        null,
        null,
        "{{ value_json.raw_value | round(0) }}",
        "mdi:brightness-6",
    ) { SensorPayload.RawValuePayload(it) }
).associateBy { it.type }


fun createDiscoveryMessageDto(): DeviceConfig {
    val sensors = availableSensors.values.associate {
        it.haIdentifier to DeviceConfig.Component.Sensor(
            name = it.humanReadableName,
            deviceClass = it.deviceClass,
            unitOfMeasurement = it.unitOfMeasurement,
            valueTemplate = it.valueTemplate,
            stateTopic = it.topic,
            uniqueId = "${DEVICE_ID}_${it.haIdentifier}",
            icon = it.icon,
            stateClass = "measurement",
        )
    }

    return DeviceConfig(
        availability = DeviceConfig.Availability(availabilityTopic),
        device = DeviceConfig.DeviceInfo(
            identifiers = listOf("octoglow"),
            name = "Octoglow",
            manufacturer = "Michał Słomkowski",
            model = "Octoglow VFD Fallout-inspired display",
        ),
        origin = DeviceConfig.DeviceDetails(
            name = "Octoglow",
            url = "https://slomkowski.eu/octoglow-vfd-fallout-inspired-display"
        ),
        components = sensors.plus(
            listOf(
                magicEyeIdentifier to DeviceConfig.Component.Switch(
                    name = "Oko magiczne",
                    stateTopic = magicEyeSwitchTopic,
                    commandTopic = magicEyeSwitchSetTopic,
                    uniqueId = "${DEVICE_ID}_$magicEyeIdentifier",
                ),
                "${dialIdentifier}_$dialCommandCcw" to DeviceConfig.Component.Button(
                    name = "Gałka w lewo",
                    uniqueId = "${DEVICE_ID}_${dialIdentifier}_$dialCommandCcw",
                    commandTopic = dialButtonTopic,
                    payloadPress = dialCommandCcw,
                ),
                "${dialIdentifier}_$dialCommandCw" to DeviceConfig.Component.Button(
                    name = "Gałka w prawo",
                    uniqueId = "${DEVICE_ID}_${dialIdentifier}_$dialCommandCw",
                    commandTopic = dialButtonTopic,
                    payloadPress = dialCommandCw,
                ),
                "${dialIdentifier}_$dialCommandPress" to DeviceConfig.Component.Button(
                    name = "Gałka przycisk",
                    uniqueId = "${DEVICE_ID}_${dialIdentifier}_$dialCommandPress",
                    commandTopic = dialButtonTopic,
                    payloadPress = dialCommandPress,
                )
            )
        ),
    )
}