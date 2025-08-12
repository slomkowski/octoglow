@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.CryptocurrencyView
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class CryptocurrencyDataHarvester(
    config: Config,
    dataSnapshotBus: DataSnapshotBus,
) : DataHarvester(logger, 10.minutes, dataSnapshotBus) {

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

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val COINPAPRIKA_API_BASE = "https://api.coinpaprika.com/v1"

        suspend fun getLatestOhlc(coinId: String): OhlcDto {
            val url = "$COINPAPRIKA_API_BASE/coins/$coinId/ohlcv/today"
            logger.debug { "Downloading OHLC info from $url" }

            return httpClient.get {
                url(url)
            }.body<List<OhlcDto>>().single()
        }


        fun fillAvailableCryptocurrencies(): Set<CoinInfoDto> = CryptocurrencyView::class.java
            .getResourceAsStream("/coinpaprika-cryptocurrencies.json").use {
                jsonSerializer.decodeFromString(it.readToString())
            }

    }

    private val availableCoins: Set<CoinInfoDto> = fillAvailableCryptocurrencies()

    private fun findCoinId(symbol: String): String = checkNotNull(availableCoins.find { it.symbol == symbol }?.id)
    { "coin with symbol '$symbol' not found" }

    private val coinKeys = config.cryptocurrencies.let { listOf(it.coin1, it.coin2, it.coin3) }

    init {
        coinKeys.forEach { findCoinId(it) }
    }

    override suspend fun pollForNewData(now: Instant): Unit = coroutineScope {
        val measurements = coinKeys.map { symbol ->
            async {
                val coinId = findCoinId(symbol)
                val dbKey = Cryptocurrency(symbol)
                try {
                    val ohlc = getLatestOhlc(coinId)
                    val value = ohlc.close
                    logger.info { "Value of $symbol at $now is \$$value." }
                    StandardDataSample(dbKey, Result.success(value))
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update status on $symbol." }
                    StandardDataSample(dbKey, Result.failure(e))
                }
            }
        }.awaitAll()

        publish(StandardDataSnapshot(now, pollingInterval, measurements))
    }
}