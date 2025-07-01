package eu.slomkowski.octoglow.octoglowd

sealed class HistoricalValueType {
    companion object {
        private fun toSnakeCase(s: String) = Regex("([A-Z])").replace(s, "_$1").uppercase().trim('_')
    }

    open val databaseSymbol: String
        get() = toSnakeCase(checkNotNull(this::class.simpleName))

    override fun toString(): String = databaseSymbol
}

object RadioactivityCpm : HistoricalValueType()
object RadioactivityUSVH : HistoricalValueType() // uSv/h

object OutdoorTemperature : HistoricalValueType()
object OutdoorHumidity : HistoricalValueType()
object OutdoorWeakBattery : HistoricalValueType()
object IndoorTemperature : HistoricalValueType()
object IndoorWeakBattery : HistoricalValueType()
object IndoorHumidity : HistoricalValueType()
object IndoorCo2 : HistoricalValueType()

object Bme280Temperature : HistoricalValueType()
object Bme280Humidity : HistoricalValueType()

object Scd40Temperature : HistoricalValueType()
object Scd40Humidity : HistoricalValueType()

object RealPressure : HistoricalValueType()
object MSLPressure : HistoricalValueType() {
    override val databaseSymbol: String
        get() = "MSL_PRESSURE"
}

data class Cryptocurrency(val symbol: String) : HistoricalValueType() {
    init {
        require(symbol.isNotBlank())
        require(symbol.length < 10)
    }

    override val databaseSymbol: String
        get() = "CRYPTOCURRENCY_$symbol".uppercase()
}

data class AirQuality(val stationId: Long) : HistoricalValueType() {
    init {
        require(stationId > 0)
    }

    override val databaseSymbol: String
        get() = "AIR_QUALITY_$stationId"
}


data class Stock(val symbol: String) : HistoricalValueType() {

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

enum class ChangeableSetting {
    BRIGHTNESS,
}
