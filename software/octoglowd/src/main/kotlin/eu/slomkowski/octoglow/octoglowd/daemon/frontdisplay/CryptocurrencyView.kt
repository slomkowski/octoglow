package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class CryptocurrencyView(
    private val config: Config,
    private val database: DatabaseLayer,
    hardware: Hardware,
) : FrontDisplayView(
    hardware,
    "Cryptocurrencies",
    10.minutes,
    15.seconds,
    13.seconds
) {

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val HISTORIC_VALUES_LENGTH = 14

        private const val COINPAPRIKA_API_BASE = "https://api.coinpaprika.com/v1"

        suspend fun getLatestOhlc(coinId: String): OhlcDto {
            val url = "$COINPAPRIKA_API_BASE/coins/$coinId/ohlcv/today"
            logger.debug { "Downloading OHLC info from $url" }

            return httpClient.get {
                url(url)
                header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:135.0) Gecko/20100101 Firefox/135.0")
            }.body<List<OhlcDto>>().single()
        }

        fun formatDollars(amount: Double?): String {
            return when (amount) {
                null -> "$-----"
                in 100_000.0..1_000_000.0 -> String.format("$%3.0fk", amount / 1000.0)
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
        val coins: Map<String, CoinReport>
    )

    private val availableCoins: Set<CoinInfoDto> = fillAvailableCryptocurrencies()
    private var currentReport: CurrentReport? = null

    private val coinKeys = config.cryptocurrencies.let { listOf(it.coin1, it.coin2, it.coin3) }

    init {
        coinKeys.forEach { findCoinId(it) }
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
                val c = config.cryptocurrencies
                val diffChartStep = c.diffChartFraction
                logger.debug { "Refreshing cryptocurrency screen, diff chart step: $diffChartStep." }
                launch { drawCurrencyInfo(report?.coins?.get(c.coin1), 0, diffChartStep) }
                launch { drawCurrencyInfo(report?.coins?.get(c.coin2), 7, diffChartStep) }
                launch { drawCurrencyInfo(report?.coins?.get(c.coin3), 14, diffChartStep) }
            }

            drawProgressBar(report?.timestamp, now)

            Unit
        }

    /**
     * Progress bar is dependent only on current time so always success.
     */
    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {

        val newReport = CurrentReport(now, coinKeys.map { symbol ->
            async {
                val coinId = findCoinId(symbol)
                try {
                    val ohlc = getLatestOhlc(coinId)
                    val value = ohlc.close
                    val dbKey = Cryptocurrency(symbol)
                    logger.info { "Value of $symbol at $now is \$$value." }
                    database.insertHistoricalValueAsync(ohlc.timeClose, dbKey, value)
                    val history =
                        database.getLastHistoricalValuesByHourAsync(now, dbKey, HISTORIC_VALUES_LENGTH).await()
                    symbol to CoinReport(symbol, value, history)
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