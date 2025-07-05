package eu.slomkowski.octoglow.octoglowd

import kotlinx.datetime.LocalTime
import org.apache.commons.lang3.RandomUtils
import java.net.URI
import java.nio.file.Paths

val testConfig = Config.parse(Paths.get("config.json"))

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
        port = RandomUtils.insecure().randomInt(10_000, 30_000),
    ),
    todoist = ConfTodoist(
        apiKey = "api-key-here"
    )
)
