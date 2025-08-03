@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface DataSampleType

interface DbDataSampleType : DataSampleType {
    val databaseSymbol: String
        get() = toSnakeCase(checkNotNull(this::class.simpleName))
}

data object RadioactivityCpm : DbDataSampleType
data object RadioactivityUSVH : DbDataSampleType // uSv/h

data object OutdoorTemperature : DbDataSampleType
data object OutdoorHumidity : DbDataSampleType
data object OutdoorWeakBattery : DbDataSampleType
data object IndoorTemperature : DbDataSampleType
data object IndoorWeakBattery : DbDataSampleType
data object IndoorHumidity : DbDataSampleType
data object IndoorCo2 : DbDataSampleType

data object Bme280Temperature : DbDataSampleType
data object Bme280Humidity : DbDataSampleType

data object Scd40Temperature : DbDataSampleType
data object Scd40Humidity : DbDataSampleType

data object RealPressure : DbDataSampleType
data object MSLPressure : DbDataSampleType {
    override val databaseSymbol: String
        get() = "MSL_PRESSURE"
}

data object LightSensorValue : DbDataSampleType

data class Cryptocurrency(val symbol: String) : DbDataSampleType {
    init {
        require(symbol.isNotBlank())
        require(symbol.length < 10)
    }

    override val databaseSymbol: String
        get() = "CRYPTOCURRENCY_$symbol".uppercase()
}

data class NbpCurrency(val symbol: String) : DbDataSampleType {
    init {
        require(symbol.isNotBlank())
        require(symbol.length < 10)
    }

    override val databaseSymbol: String
        get() = "NBP_$symbol".uppercase()
}

data class AirQuality(val stationId: Long) : DbDataSampleType {
    init {
        require(stationId > 0)
    }

    override val databaseSymbol: String
        get() = "AIR_QUALITY_$stationId"
}

data class Stock(val symbol: String) : DbDataSampleType {

    companion object {
        private val NON_ALPHANUMERIC_REGEX = Regex("[^A-Za-z0-9 ]")
    }

    init {
        require(symbol.isNotBlank())
        require(symbol.length < 12)
    }

    override val databaseSymbol: String
        get() = "STOCK_" + symbol.replace(NON_ALPHANUMERIC_REGEX, "_").uppercase()
}

data object PingTimeRemoteHost : DataSampleType

data object PingTimeGateway : DbDataSampleType

data class MagicEyeStateChanged(
    override val timestamp: Instant,
    val enabled: Boolean
) : Snapshot

interface DataSample {
    val type: DataSampleType
    val value: Result<Double>
}

data class StandardDataSample(
    override val type: DataSampleType,
    override val value: Result<Double>,
) : DataSample

interface Snapshot {
    val timestamp: Instant
}

interface DataSnapshot : Snapshot {
    val cycleLength: Duration
    val values: List<DataSample>
}

data class StandardDataSnapshot(
    override val timestamp: Instant,
    override val cycleLength: Duration,
    override val values: List<DataSample>,
) : DataSnapshot {
    init {
        require(values.isNotEmpty()) { "At least one value is required" }
    }
}

enum class ChangeableSetting {
    BRIGHTNESS,
}
