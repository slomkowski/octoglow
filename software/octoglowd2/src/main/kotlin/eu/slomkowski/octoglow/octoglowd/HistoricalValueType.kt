package eu.slomkowski.octoglow.octoglowd

sealed class HistoricalValueType {
    companion object {
        private fun toSnakeCase(s: String) = Regex("([A-Z])").replace(s, "_$1").toUpperCase().trim('_')
    }

    open val databaseSymbol: String
        get() = toSnakeCase(checkNotNull(this::class.simpleName))

    override fun toString(): String = databaseSymbol
}

object OutdoorTemperature : HistoricalValueType()
object OutdoorHumidity : HistoricalValueType()
object OutdoorWeakBattery : HistoricalValueType()

data class Cryptocurrency(val symbol: String) : HistoricalValueType() {
    init {
        require(symbol.isNotBlank())
        require(symbol.length < 10)
    }

    override val databaseSymbol: String
        get() = "CRYPTOCURRENCY_$symbol".toUpperCase()
}
