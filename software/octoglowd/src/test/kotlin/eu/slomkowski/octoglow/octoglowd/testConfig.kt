package eu.slomkowski.octoglow.octoglowd

import kotlinx.datetime.LocalTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import java.net.URI
import java.nio.file.Paths
import kotlin.random.Random

/**
 * Real credentials for external services (radmon.org, simplemonitor, Todoist) that must not be
 * committed. Only tests that actually call those services read them via [testConfig]; everything
 * else uses [defaultTestConfig]. Sections absent from the file fall back to the defaults.
 */
@Serializable
private data class TestCredentials(
    val simplemonitor: ConfSimpleMonitor? = null,
    val radmon: ConfRadmon? = null,
    val todoist: ConfTodoist? = null,
)

// Loaded lazily from the test classpath (src/test/resources/test-config.json) so it resolves
// regardless of the test's working directory, and a missing file only fails the tests that use it.
@OptIn(ExperimentalSerializationApi::class)
val testConfig: Config by lazy {
    val resource = "/test-config.json"
    val stream = checkNotNull(TestCredentials::class.java.getResourceAsStream(resource)) {
        "$resource not found on the test classpath (expected in src/test/resources)"
    }
    val creds = stream.use { jsonSerializer.decodeFromStream<TestCredentials>(it) }
    defaultTestConfig.copy(
        simplemonitor = creds.simplemonitor ?: defaultTestConfig.simplemonitor,
        radmon = creds.radmon ?: defaultTestConfig.radmon,
        todoist = creds.todoist ?: defaultTestConfig.todoist,
    )
}

val defaultTestConfig = Config(
    i2cBus = 0,
    airQuality = ConfAirQuality(
        station1 = ConfSingleAirStation(id = 1, name = ""),
        station2 = ConfSingleAirStation(id = 0, name = "")
    ),
    cryptocurrencies = ConfCryptocurrencies(coin1 = "BTC", coin2 = "ETH", coin3 = "DOGE"),
    geoPosition = ConfGeoPosition(
        latitude = 52.395869,
        longitude = 16.929220,
        elevation = 50.0
    ),
    simplemonitor = ConfSimpleMonitor(url = URI("")),
    sleep = ConfSleep(startAt = LocalTime(22, 0)),
    databaseFile = Paths.get("test-data.db"),
    nbp = ConfNbp(currency1 = "USD", currency2 = "EUR", currency3 = "CHF"),
    networkInfo = ConfNetworkInfo(
        pingAddress = "127.0.0.1"
    ),
    remoteSensors = ConfRemoteSensors(
        indoorChannelId = 0,
        outdoorChannelId = 1
    ),
    mqtt = ConfMqttInfo(
        enabled = true,
        port = Random.nextInt(10_000, 30_000),
    ),
    todoist = ConfTodoist(
        apiKey = "api-key-here"
    ),
    garbageCollectionTimetable = ConfGarbageCollectionTimetable(
        streetName = "KOLEGIACKI",
        buildingNumber = "17",
    )
)
