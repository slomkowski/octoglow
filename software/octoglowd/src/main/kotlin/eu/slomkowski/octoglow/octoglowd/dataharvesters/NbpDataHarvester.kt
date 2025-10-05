@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.dataharvesters.SingleNbpCurrencyDataSample.Companion.HISTORIC_VALUES_LENGTH
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class SingleNbpCurrencyDataSample(
    val code: String,
    val isLatestFromToday: Boolean?,
    override val value: Result<Double>,
    val historical: List<Double?>?,
) : DataSample {
    companion object {
        const val HISTORIC_VALUES_LENGTH = 14
    }

    init {
        historical?.let {
            require(it.size == HISTORIC_VALUES_LENGTH)
        }
    }

    override val type = NbpCurrency(code)
}

class NbpDataHarvester(
    config: Config,
    eventBus: DataSnapshotBus,
) : DataHarvester(logger, 10.minutes, eventBus) {

    interface DatedPrice {
        val date: LocalDate
        val price: Double
    }

    @Serializable
    data class RateDto(
        val no: String,
        @Serializable(LocalDateSerializer::class)
        val effectiveDate: LocalDate,
        val mid: Double
    ) : DatedPrice {
        override val date: LocalDate
            get() = effectiveDate
        override val price: Double
            get() = mid
    }

    @Serializable
    data class CurrencyDto(
        val table: String,
        val currency: String,
        val code: String,
        val rates: List<RateDto>,
    )

    @Serializable
    data class GoldPrice(
        @Serializable(LocalDateSerializer::class)
        @SerialName("data")
        override val date: LocalDate,

        @SerialName("cena")
        override val price: Double
    ) : DatedPrice


    companion object {
        private val logger = KotlinLogging.logger {}

        private const val OUNCE = 31.1034768 // grams

        private const val NBP_API_BASE = "https://api.nbp.pl/api/"

        suspend fun getCurrencyRates(currencyCode: String, howMany: Int): List<RateDto> {
            require(currencyCode.isNotBlank())
            require(howMany > 0)
            logger.debug { "Downloading currency rates for $currencyCode." }
            val url = "$NBP_API_BASE/exchangerates/rates/a/$currencyCode/last/$howMany"

            val resp: CurrencyDto = httpClient.get(url).body()

            return resp.rates
        }

        suspend fun getGoldRates(howMany: Int): List<GoldPrice> {
            require(howMany > 0)
            logger.debug { "Downloading gold price." }
            val url = "$NBP_API_BASE/cenyzlota/last/$howMany"

            val resp: List<GoldPrice> = httpClient.get(url).body()

            return resp.apply {
                check(isNotEmpty())
            }
        }

        fun createReport(code: String, rates: List<DatedPrice>, today: LocalDate): SingleNbpCurrencyDataSample {
            val mostRecentRate = checkNotNull(rates.maxByOrNull { it.date })

            val historical = ((1)..HISTORIC_VALUES_LENGTH).map { dayNumber ->
                val day = today.minus(dayNumber, DateTimeUnit.DAY)
                rates.singleOrNull { it.date == day }?.price
            }.asReversed()

            return SingleNbpCurrencyDataSample(
                code,
                mostRecentRate.date == today,
                Result.success(mostRecentRate.price),
                historical
            )
        }

        suspend fun getCurrencyReport(code: String, today: LocalDate): SingleNbpCurrencyDataSample {
            val rates = when (code) {
                "XAU" -> getGoldRates(HISTORIC_VALUES_LENGTH).map { r -> GoldPrice(r.date, OUNCE * r.price) }
                else -> getCurrencyRates(code, HISTORIC_VALUES_LENGTH)
            }
            return createReport(code, rates, today)
        }
    }

    private val currencyKeys = listOf(config.nbp.currency1, config.nbp.currency2, config.nbp.currency3).onEach {
        check(it.length == 3) { "invalid currency code $it" }
    }

    override suspend fun pollForNewData(now: Instant): Unit = coroutineScope {
        val newReports: List<SingleNbpCurrencyDataSample> = currencyKeys.map { code ->
            async {
                try {
                    getCurrencyReport(
                        code,
                        now.toLocalDateInCurrentTimeZone(),
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update status on $code" }
                    SingleNbpCurrencyDataSample(
                        code,
                        null,
                        Result.failure(e),
                        null,
                    )
                }
            }
        }.awaitAll()

        publish(StandardDataSnapshot(now, pollingInterval, newReports))
    }
}