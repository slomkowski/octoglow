package eu.slomkowski.octoglow.octoglowd

sealed class MeasurementType {
    companion object {
        private fun toSnakeCase(s: String) = Regex("([A-Z])").replace(s, "_$1").uppercase().trim('_')
    }

    open val databaseSymbol: String
        get() = toSnakeCase(checkNotNull(this::class.simpleName))

    override fun toString(): String = databaseSymbol
}


object RadioactivityCpm : MeasurementType()
object RadioactivityUSVH : MeasurementType() // uSv/h

object OutdoorTemperature : MeasurementType()
object OutdoorHumidity : MeasurementType()
object OutdoorWeakBattery : MeasurementType()
object IndoorTemperature : MeasurementType()
object IndoorWeakBattery : MeasurementType()
object IndoorHumidity : MeasurementType()
object IndoorCo2 : MeasurementType()

object Bme280Temperature : MeasurementType()
object Bme280Humidity : MeasurementType()

object Scd40Temperature : MeasurementType()
object Scd40Humidity : MeasurementType()

object RealPressure : MeasurementType()
object MSLPressure : MeasurementType() {
    override val databaseSymbol: String
        get() = "MSL_PRESSURE"
}

object LightSensorValue : MeasurementType()

data class Cryptocurrency(val symbol: String) : MeasurementType() {
    init {
        require(symbol.isNotBlank())
        require(symbol.length < 10)
    }

    override val databaseSymbol: String
        get() = "CRYPTOCURRENCY_$symbol".uppercase()
}

data class AirQuality(val stationId: Long) : MeasurementType() {
    init {
        require(stationId > 0)
    }

    override val databaseSymbol: String
        get() = "AIR_QUALITY_$stationId"
}


data class Stock(val symbol: String) : MeasurementType() {

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
