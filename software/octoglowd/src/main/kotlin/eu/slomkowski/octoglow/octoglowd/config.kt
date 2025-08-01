package eu.slomkowski.octoglow.octoglowd

import kotlinx.datetime.LocalTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds


@Serializable
data class ConfGeoPosition(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double, // in meters, above sea level
) {
    init {
        require(latitude in (-90.0..90.0)) { "latitude has to be between -90 and 90" }
        require(longitude in (-180.0..180.0)) { "longitude has to be between -180 and 180" }
        require(elevation in (-200.0..8000.0)) { "invalid elevation" }
    }
}

@Serializable
data class ConfSleep(
    val startAt: LocalTime,
    val duration: Duration = 8.hours
)

@Serializable
data class ConfSimpleMonitor(
    @Serializable(UriSerializer::class)
    val url: URI,
    val user: String = "",
    val password: String = "",
)

@Serializable
data class ConfSingleAirStation(
    val id: Long,
    val name: String,
)

@Serializable
data class ConfAirQuality(
    val station1: ConfSingleAirStation,
    val station2: ConfSingleAirStation
)

@Serializable
data class ConfCryptocurrencies(
    val coin1: String,
    val coin2: String,
    val coin3: String,
    val diffChartFraction: Double = 0.005
)

@Serializable
data class ConfRemoteSensors(
    val indoorChannelId: Int,
    val outdoorChannelId: Int,
)

@Serializable
data class ConfNbp(
    val currency1: String,
    val currency2: String,
    val currency3: String,
    val diffChartFraction: Double = 0.005
)

@Serializable
data class ConfNetworkInfo(
    val pingBinary: Path = Paths.get("/bin/ping"),
    // IP address or domain used to check internet access on network view
    val pingAddress: String,
)

@Serializable
data class ConfMqttInfo(
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = 1883,
    val username: String = "",
    val password: String = "",
    val homeassistantDiscoveryPrefix: String = "homeassistant",
)

@Serializable
data class ConfTodoist(
    val apiKey: String,
) {
    init {
        require(apiKey.isNotBlank()) { "Todoist API key must not be blank" }
    }
}

@Serializable
data class ConfRadmon(
    val enabled: Boolean = false,
    val username: String = "",
    val password: String = "",
) {
    init {
        if (enabled) {
            require(username.isNotBlank()) { "radmon.org username must not be blank" }
            require(password.isNotBlank()) { "radmon.org password must not be blank" }
        }
    }
}

@Serializable
data class Config(
    val i2cBus: Int,
    val databaseFile: Path = Paths.get("data.db"),

    val countryCode: String = "PL",

    // When the dial is used, the device goes to manual mode. After this timeout, it switches back to automatic views cycling.
    val viewAutomaticCycleTimeout: Duration = 40.seconds,

    val airQuality: ConfAirQuality,

    val geoPosition: ConfGeoPosition,

    val sleep: ConfSleep,

    val simplemonitor: ConfSimpleMonitor,

    val cryptocurrencies: ConfCryptocurrencies,

    val remoteSensors: ConfRemoteSensors,

    val nbp: ConfNbp,

    val networkInfo: ConfNetworkInfo,

    val mqtt: ConfMqttInfo,

    val todoist: ConfTodoist,

    val radmon: ConfRadmon? = null,
) {
    init {
        require(i2cBus >= 0)
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun parse(filePath: Path): Config {
            Files.newInputStream(filePath).use {
                return Json.decodeFromStream(it)
            }
        }
    }
}
