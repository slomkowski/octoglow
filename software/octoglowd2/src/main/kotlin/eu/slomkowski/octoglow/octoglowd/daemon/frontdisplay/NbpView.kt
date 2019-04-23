package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.RequiredItem
import eu.slomkowski.octoglow.octoglowd.NbpKey
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.jacksonObjectMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KLogging
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class NbpView(
        private val config: Config,
        private val hardware: Hardware)
    : FrontDisplayView("NBP exchange rates",
        Duration.ofMinutes(15),
        Duration.ofSeconds(15),
        Duration.ofSeconds(36)) {

    interface DatedPrice {
        val date: LocalDate
        val price: Double
    }

    data class CurrencyRate(
            val no: String,
            val effectiveDate: LocalDate,
            val mid: Double) : DatedPrice {
        override val date: LocalDate
            get() = effectiveDate
        override val price: Double
            get() = mid
    }

    //todo refactor names
    data class CurrencyInfo(
            val table: String,
            val currency: String,
            val code: String,
            val rates: List<CurrencyRate>)

    data class CurrencyReport(
            val code: String,
            val isLatestFromToday: Boolean,
            val latest: Double,
            val historical: List<Double?>) {
        init {
            require(historical.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class CurrentReport(
            val timestamp: LocalDateTime,
            val currencies: Map<RequiredItem<String>, CurrencyReport>)

    data class GoldPrice(
            @JsonProperty("data")
            override val date: LocalDate,
            @JsonProperty("cena")
            override val price: Double) : DatedPrice

    companion object : KLogging() {
        private const val OUNCE = 31.1034768 // grams

        private const val HISTORIC_VALUES_LENGTH = 14

        private const val NBP_API_BASE = "http://api.nbp.pl/api/"

        suspend fun getCurrencyRates(currencyCode: String, howMany: Int): List<CurrencyRate> {
            require(currencyCode.isNotBlank())
            require(howMany > 0)
            logger.debug { "Downloading currency rates for $currencyCode." }
            val url = "$NBP_API_BASE/exchangerates/rates/a/$currencyCode/last/$howMany"

            val resp = Fuel.get(url).awaitObject<CurrencyInfo>(jacksonDeserializerOf(jacksonObjectMapper))
            logger.debug { "Got: $resp" }

            return resp.let {
                check(it.code == currencyCode)
                check(it.rates.isNotEmpty())
                it.rates
            }
        }

        suspend fun getGoldRates(howMany: Int): List<GoldPrice> {
            require(howMany > 0)
            logger.debug { "Downloading gold price." }
            val url = "$NBP_API_BASE/cenyzlota/last/$howMany"

            val resp = Fuel.get(url).awaitObject<List<GoldPrice>>(jacksonDeserializerOf(jacksonObjectMapper))
            logger.debug { "Got: $resp" }

            return resp.apply {
                check(isNotEmpty())
            }
        }

        fun createReport(code: String, rates: List<DatedPrice>, today: LocalDate): CurrencyReport {
            val mostRecentRate = checkNotNull(rates.maxBy { it.date })

            val historical = ((1)..HISTORIC_VALUES_LENGTH).map { dayNumber ->
                val day = today.minusDays(dayNumber.toLong())
                rates.singleOrNull { it.date == day }?.price
            }.asReversed()

            return CurrencyReport(
                    code,
                    mostRecentRate.date == today,
                    mostRecentRate.price,
                    historical)
        }

        suspend fun getCurrencyReport(code: String, today: LocalDate): CurrencyReport {
            val rates = when (code) {
                "XAU" -> getGoldRates(HISTORIC_VALUES_LENGTH).map { r -> GoldPrice(r.date, OUNCE * r.price) }
                else -> getCurrencyRates(code, HISTORIC_VALUES_LENGTH)
            }
            return createReport(code, rates, today)
        }
    }

    private var currentReport: CurrentReport? = null

    private suspend fun drawCurrencyInfo(cr: CurrencyReport?, offset: Int, diffChartStep: Double) {
        require(diffChartStep > 0)
        TODO()
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
        TODO()
    }

    /**
     * Progress bar is dependent only on current time so always success.
     */
    override suspend fun poolInstantData(): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun poolStatusData(): UpdateStatus = coroutineScope {
        val now = LocalDateTime.now()
        val currencyKeys = listOf(NbpKey.currency1, NbpKey.currency2, NbpKey.currency3)

        val newReport = CurrentReport(now, currencyKeys.map { currencyKey ->
            async {
                val code = config[currencyKey]
                try {
                    currencyKey to getCurrencyReport(code, now.toLocalDate())
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update status on $code." }
                    null
                }
            }
        }.awaitAll().filterNotNull().toMap())

        currentReport = newReport

        when (newReport.currencies.size) {
            3 -> UpdateStatus.FULL_SUCCESS
            0 -> UpdateStatus.FAILURE
            else -> UpdateStatus.PARTIAL_SUCCESS
        }
    }
}