package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.RequiredItem
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import mu.KLogging
import kotlin.time.ExperimentalTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@ExperimentalTime
class CryptocurrencyView(
    private val config: Config,
    private val database: DatabaseLayer,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "Cryptocurrencies",
    10.minutes,
    15.seconds,
    13.seconds
) {

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14

        private const val COINPAPRIKA_API_BASE = "https://api.coinpaprika.com/v1"

        suspend fun getLatestOhlc(coinId: String): OhlcDto {
            val url = "$COINPAPRIKA_API_BASE/coins/$coinId/ohlcv/today"
            logger.debug { "Downloading OHLC info from $url" }

            return httpClient.get<List<OhlcDto>>(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:59.0) Gecko/20100101")
            }.single()
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

        fun fillAvailableCryptocurrencies(): Set<CoinInfoDto> = CryptocurrencyView::class.java
            .getResourceAsStream("/coinpaprika-cryptocurrencies.json").use {
                jsonSerializer.decodeFromString(it.readToString())
            }
    }

    @Serializable
    data class CoinInfoDto(
        val id: String,
        val name: String,
        val symbol: String
    )

    @Serializable
    data class OhlcDto(
        @Serializable(InstantSerializer::class)
        @SerialName("time_open")
        val timeOpen: Instant,

        @Serializable(InstantSerializer::class)
        @SerialName("time_close")
        val timeClose: Instant,

        val open: Double,
        val close: Double,
        val low: Double,
        val high: Double
    ) {
        init {
            require(timeClose >= timeOpen)
            require(low <= high)
            doubleArrayOf(low, high, open, close).forEach { require(it > 0) }
        }
    }

    data class CoinReport(
        val symbol: String,
        val latest: Double,
        val historical: List<Double?>
    ) {
        init {
            require(historical.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class CurrentReport(
        val timestamp: Instant,
        val coins: Map<RequiredItem<String>, CoinReport>
    )

    private val availableCoins: Set<CoinInfoDto> = fillAvailableCryptocurrencies()
    private var currentReport: CurrentReport? = null

    private val coinKeys = listOf(CryptocurrenciesKey.coin1, CryptocurrenciesKey.coin2, CryptocurrenciesKey.coin3)

    init {
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

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val report = currentReport

            if (redrawStatus) {
                val diffChartStep = config[CryptocurrenciesKey.diffChartFraction]
                logger.debug { "Refreshing cryptocurrency screen, diff chart step: $diffChartStep." }
                launch { drawCurrencyInfo(report?.coins?.get(CryptocurrenciesKey.coin1), 0, diffChartStep) }
                launch { drawCurrencyInfo(report?.coins?.get(CryptocurrenciesKey.coin2), 7, diffChartStep) }
                launch { drawCurrencyInfo(report?.coins?.get(CryptocurrenciesKey.coin3), 14, diffChartStep) }
            }

            drawProgressBar(report?.timestamp, now)

            Unit
        }

    /**
     * Progress bar is dependent only on current time so always success.
     */
    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {

        val newReport = CurrentReport(now, coinKeys.map { coinKey ->
            async {
                val symbol = config[coinKey]
                val coinId = findCoinId(symbol)
                try {
                    val ohlc = getLatestOhlc(coinId)
                    val value = ohlc.close
                    val dbKey = Cryptocurrency(symbol)
                    logger.info { "Value of $symbol at $now is \$$value." }
                    database.insertHistoricalValueAsync(ohlc.timeClose, dbKey, value)
                    val history =
                        database.getLastHistoricalValuesByHourAsync(now, dbKey, HISTORIC_VALUES_LENGTH).await()
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