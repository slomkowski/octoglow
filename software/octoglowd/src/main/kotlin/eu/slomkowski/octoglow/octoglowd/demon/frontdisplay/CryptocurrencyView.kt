package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@OptIn(ExperimentalTime::class)
class CryptocurrencyView(
    private val config: Config,
    private val database: DatabaseDemon,
    hardware: Hardware,
) : FrontDisplayView<CryptocurrencyView.CurrentReport, Unit>(
    hardware,
    "Cryptocurrencies",
    null,
    logger,
) {
    override fun preferredDisplayTime(status: CurrentReport?) = 13.seconds

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val HISTORIC_VALUES_LENGTH = 14

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
        val cycleLength: Duration,
        val coins: Map<String, CoinReport>
    )

    override suspend fun onNewDataSnapshot(
        report: DataSnapshot,
        oldStatus: CurrentReport?
    ): UpdateStatus = coroutineScope {
        if (report !is StandardDataSnapshot) {
            return@coroutineScope UpdateStatus.NoNewData
        }

        suspend fun getForCoin(coinSymbol: String): CoinReport? {
            val dbKey = Cryptocurrency(coinSymbol)
            val coinData = report.values.firstOrNull { it.type == dbKey }?.value?.getOrNull()
                ?: return null

            val history = database.getLastHistoricalValuesByHourAsync(
                report.timestamp,
                dbKey,
                HISTORIC_VALUES_LENGTH,
            ).await()

            return CoinReport(
                coinSymbol,
                coinData,
                history
            )
        }

        val coins = config.cryptocurrencies.let { c ->
            listOf(c.coin1, c.coin2, c.coin3)
                .map { async { getForCoin(it)?.let { report -> it to report } } }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }

        if (coins.isEmpty()) {
            return@coroutineScope UpdateStatus.NoNewData
        }

        return@coroutineScope UpdateStatus.NewData(
            CurrentReport(
                report.timestamp,
                report.cycleLength,
                coins
            )
        )
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

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: CurrentReport?,
        instant: Unit?
    ): Unit = coroutineScope {
        if (redrawStatus) {
            val c = config.cryptocurrencies
            val diffChartStep = c.diffChartFraction
            logger.debug { "Refreshing cryptocurrency screen, diff chart step: $diffChartStep." }
            launch { drawCurrencyInfo(status?.coins?.get(c.coin1), 0, diffChartStep) }
            launch { drawCurrencyInfo(status?.coins?.get(c.coin2), 7, diffChartStep) }
            launch { drawCurrencyInfo(status?.coins?.get(c.coin3), 14, diffChartStep) }
        }

        drawProgressBar(status?.timestamp, now, status?.cycleLength)
    }
}