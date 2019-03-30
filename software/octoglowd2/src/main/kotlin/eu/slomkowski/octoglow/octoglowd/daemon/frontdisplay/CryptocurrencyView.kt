package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.RequiredItem
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.*
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

class CryptocurrencyView(
        coroutineContext: CoroutineContext,
        private val config: Config,
        private val database: DatabaseLayer,
        private val hardware: Hardware)
    : FrontDisplayView("Cryptocurrencies", Duration.ofMinutes(5), Duration.ofSeconds(15)) {

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

        fun formatDollars(amount: Double?): String {
            return when (amount) {
                null -> "$-----"
                in 1000.0..100_000.0 -> String.format("$%4.0f", amount)
                in 100.0..1000.0 -> String.format("$%5.1f", amount)
                in 10.0..100.0 -> String.format("$%5.2f", amount)
                else -> String.format("$%5.3f", amount)
            }
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

    data class CoinReport(
            val symbol: String,
            val latest: Double,
            val historical: List<Double?>) {
        init {
            require(historical.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class CurrentReport(
            val timestamp: LocalDateTime,
            val coins: Map<RequiredItem<String>, CoinReport>)

    private val availableCoins: List<CoinInfoDto>
    private var currentReport: CurrentReport? = null

    private val coinKeys = listOf(CryptocurrenciesKey.coin1, CryptocurrenciesKey.coin2, CryptocurrenciesKey.coin3)

    init {
        availableCoins = runBlocking(coroutineContext) { getCoins() }
        coinKeys.forEach { findCoinId(config[it]) }
    }

    private suspend fun drawCurrencyInfo(cr: CoinReport?, offset: Int, diffChartStep: Double) {
        require(diffChartStep > 0)
        hardware.frontDisplay.apply {
            setStaticText(offset, cr?.symbol?.take(3) ?: "---")
            setStaticText(offset + 20, formatDollars(cr?.latest))

            if (cr != null) {
                val unit = cr.latest * diffChartStep
                setOneLineDiffChart(5 * (offset + 3), cr.latest, cr.historical, unit)
            }
        }
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
        val report = currentReport
        val fd = hardware.frontDisplay

        if (redrawStatus) {
            val diffChartStep = config[CryptocurrenciesKey.diffChartFraction]
            logger.debug { "Refreshing cryptocurrency screen, diff chart step: $diffChartStep." }
            launch { drawCurrencyInfo(report?.coins?.get(CryptocurrenciesKey.coin1), 0, diffChartStep) }
            launch { drawCurrencyInfo(report?.coins?.get(CryptocurrenciesKey.coin2), 7, diffChartStep) }
            launch { drawCurrencyInfo(report?.coins?.get(CryptocurrenciesKey.coin3), 14, diffChartStep) }
        }

        if (report != null) {
            val currentCycleDuration = Duration.between(report.timestamp, LocalDateTime.now())
            check(!currentCycleDuration.isNegative)
            launch { fd.setUpperBar(listOf(getSegmentNumber(currentCycleDuration, poolStatusEvery))) }
        } else {
            launch { fd.setUpperBar(emptyList()) }
        }

        Unit
    }

    /**
     * Progress bar is dependent only on current time so always success.
     */
    override suspend fun poolInstantData(): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun poolStatusData(): UpdateStatus = coroutineScope {
        val ts = LocalDateTime.now()

        val newReport = CurrentReport(ts, coinKeys.map { coinKey ->
            async {
                val symbol = config[coinKey]
                val coinId = findCoinId(symbol)
                try {
                    val ohlc = getLatestOhlc(coinId)
                    val value = ohlc.close
                    val ohlcTimestamp = LocalDateTime.ofInstant(ohlc.timeClose.toInstant(), ZoneId.systemDefault())
                    val dbKey = Cryptocurrency(symbol)
                    logger.info { "Value of $symbol at $ts is \$$value." }
                    database.insertHistoricalValueAsync(ohlcTimestamp, dbKey, value)
                    val history = database.getLastHistoricalValuesByHourAsync(ts, dbKey, HISTORIC_VALUES_LENGTH).await()
                    coinKey to CoinReport(symbol, value, history)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update status on $symbol." }
                    null
                }
            }
        }.awaitAll().filterNotNull().toMap())

        currentReport = newReport

        when (newReport.coins.size) {
            3 -> UpdateStatus.FULL_SUCCESS
            0 -> UpdateStatus.FAILURE
            else -> UpdateStatus.PARTIAL_SUCCESS
        }
    }

    private fun findCoinId(symbol: String): String = checkNotNull(availableCoins.find { it.symbol == symbol }?.id)
    { "coin with symbol '$symbol' not found" }
}