package eu.slomkowski.octoglow.octoglowd.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.RequiredItem
import eu.slomkowski.octoglow.octoglowd.CryptocurrenciesKey
import eu.slomkowski.octoglow.octoglowd.DatabaseLayer
import eu.slomkowski.octoglow.octoglowd.jacksonObjectMapper
import kotlinx.coroutines.*
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class CryptocurrencyView(
        private val config: Config,
        private val database: DatabaseLayer) : FrontDisplayView {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14

        private const val COINPAPRIKA_API_BASE = "https://api.coinpaprika.com/v1/"

        suspend fun getLatestOhlc(coinId: String): OhlcDto {
            val url = "$COINPAPRIKA_API_BASE/coins/$coinId/ohlcv/today"
            logger.debug { "Downloading OHLC info from $url" }

            val resp = Fuel.get(url).awaitObject<OhlcDto>(jacksonDeserializerOf(jacksonObjectMapper))
            logger.debug { "Got: $resp" }

            return resp
        }

        suspend fun getCoins(): List<CoinInfoDto> {
            val url = "$COINPAPRIKA_API_BASE/coins"
            logger.debug { "Downloading coin list from $url" }
            val resp = Fuel.get(url).awaitObject<List<CoinInfoDto>>(jacksonDeserializerOf(jacksonObjectMapper))
            logger.debug { "Got list of ${resp.size} coins." }
            return resp
        }
    }

    data class CoinInfoDto(
            val id: String,
            val name: String,
            val symbol: String,
            @JsonProperty("is_active") val isActive: Boolean)

    data class OhlcDto(
            @JsonProperty("time_open") val timeOpen: OffsetDateTime,
            @JsonProperty("time_close") val timeClose: OffsetDateTime,
            val open: Double,
            val close: Double,
            val low: Double,
            val high: Double) {
        init {
            require(timeOpen.isBefore(timeClose))
            require(low <= high)
            doubleArrayOf(low, high, open, close).forEach { require(it > 0) }
        }
    }

    data class CurrentReport(
            val symbol: String,
            val timestamp: LocalDateTime,
            val latest: Double,
            val historical: List<Double?>) {
        init {
            require(historical.size == HISTORIC_VALUES_LENGTH)
        }
    }

    private val availableCoins: List<CoinInfoDto>
    private var currentReports: Map<RequiredItem<String>, CurrentReport> = emptyMap()

    init {
        availableCoins = runBlocking { getCoins() }
    }

    override suspend fun redrawDisplay() = coroutineScope {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun poolStateUpdateAsync(): Deferred<UpdateStatus> = coroutineScope {
        async {
            val newReports = listOf(CryptocurrenciesKey.coin1, CryptocurrenciesKey.coin2, CryptocurrenciesKey.coin3).map { coinKey ->
                async {
                    val symbol = config[coinKey]
                    val coinId = checkNotNull(availableCoins.find { it.symbol == symbol }?.id)
                    { "coin with symbol '$symbol' not found" }
                    try {
                        val ohlc = getLatestOhlc(coinId)
                        val ts = LocalDateTime.ofInstant(ohlc.timeClose.toInstant(), ZoneId.systemDefault())
                        val value = ohlc.close
                        logger.info { "Value of $symbol at $ts is \$$value." }
                        database.insertCryptocurrencyValue(ts, symbol, value)

                        //todo get historical data
                        coinKey to CurrentReport(symbol, ts, value, emptyList())
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to update status on $symbol." }
                        null
                    }
                }
            }.awaitAll()

            currentReports = newReports.filterNotNull().toMap()

            when (newReports.size) {
                3 -> UpdateStatus.FULL_SUCCESS
                0 -> UpdateStatus.FAILURE
                else -> UpdateStatus.PARTIAL_SUCCESS
            }
        }
    }

    override fun getPreferredPoolingInterval(): Duration = Duration.ofSeconds(30) // change to 5 min

    override val name: String
        get() = "Cryptocurrencies"
}