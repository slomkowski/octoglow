package eu.slomkowski.octoglow.octoglowd

interface MeasurementType 

interface DbMeasurementType : MeasurementType {
    val databaseSymbol: String
        get() = toSnakeCase(checkNotNull(this::class.simpleName))
}

data object RadioactivityCpm : DbMeasurementType
data object RadioactivityUSVH : DbMeasurementType // uSv/h

data object OutdoorTemperature : DbMeasurementType
data object OutdoorHumidity : DbMeasurementType
data object OutdoorWeakBattery : DbMeasurementType
data object IndoorTemperature : DbMeasurementType
data object IndoorWeakBattery : DbMeasurementType
data object IndoorHumidity : DbMeasurementType
data object IndoorCo2 : DbMeasurementType

data object Bme280Temperature : DbMeasurementType
data object Bme280Humidity : DbMeasurementType

data object Scd40Temperature : DbMeasurementType
data object Scd40Humidity : DbMeasurementType

data object RealPressure : DbMeasurementType
data object MSLPressure : DbMeasurementType {
    override val databaseSymbol: String
        get() = "MSL_PRESSURE"
}

data object LightSensorValue : DbMeasurementType

data class Cryptocurrency(val symbol: String) : DbMeasurementType {
    init {
        require(symbol.isNotBlank())
        require(symbol.length < 10)
    }

    override val databaseSymbol: String
        get() = "CRYPTOCURRENCY_$symbol".uppercase()
}

data class NbpCurrency(val symbol: String) : DbMeasurementType {
    init {
        require(symbol.isNotBlank())
        require(symbol.length < 10)
    }

    override val databaseSymbol: String
        get() = "NBP_$symbol".uppercase()
}

data class AirQuality(val stationId: Long) : DbMeasurementType {
    init {
        require(stationId > 0)
    }

    override val databaseSymbol: String
        get() = "AIR_QUALITY_$stationId"
}


data class Stock(val symbol: String) : DbMeasurementType {

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
