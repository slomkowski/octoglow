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
import kotlinx.coroutines.launch
import mu.KLogging
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class NbpView(
        private val config: Config,
        hardware: Hardware)
    : FrontDisplayView(hardware,
        "NBP exchange rates",
        Duration.ofMinutes(10),
        Duration.ofSeconds(15),
        Duration.ofSeconds(21)) {

    interface DatedPrice {
        val date: LocalDate
        val price: Double
    }

    data class RateDto(
            val no: String,
            val effectiveDate: LocalDate,
            val mid: Double) : DatedPrice {
        override val date: LocalDate
            get() = effectiveDate
        override val price: Double
            get() = mid
    }

    data class CurrencyDto(
            val table: String,
            val currency: String,
            val code: String,
            val rates: List<RateDto>)

    data class SingleCurrencyReport(
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
            val currencies: Map<RequiredItem<String>, SingleCurrencyReport>)

    data class GoldPrice(
            @JsonProperty("data")
            override val date: LocalDate,
            @JsonProperty("cena")
            override val price: Double) : DatedPrice

    companion object : KLogging() {
        private const val OUNCE = 31.1034768 // grams

        private const val HISTORIC_VALUES_LENGTH = 14

        private const val NBP_API_BASE = "http://api.nbp.pl/api/"

        suspend fun getCurrencyRates(currencyCode: String, howMany: Int): List<RateDto> {
            require(currencyCode.isNotBlank())
            require(howMany > 0)
            logger.debug { "Downloading currency rates for $currencyCode." }
            val url = "$NBP_API_BASE/exchangerates/rates/a/$currencyCode/last/$howMany"

            val resp = Fuel.get(url).awaitObject<CurrencyDto>(jacksonDeserializerOf(jacksonObjectMapper))

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

            return resp.apply {
                check(isNotEmpty())
            }
        }

        fun createReport(code: String, rates: List<DatedPrice>, today: LocalDate): SingleCurrencyReport {
            val mostRecentRate = checkNotNull(rates.maxBy { it.date })

            val historical = ((1)..HISTORIC_VALUES_LENGTH).map { dayNumber ->
                val day = today.minusDays(dayNumber.toLong())
                rates.singleOrNull { it.date == day }?.price
            }.asReversed()

            return SingleCurrencyReport(
                    code,
                    mostRecentRate.date == today,
                    mostRecentRate.price,
                    historical)
        }

        suspend fun getCurrencyReport(code: String, today: LocalDate): SingleCurrencyReport {
            val rates = when (code) {
                "XAU" -> getGoldRates(HISTORIC_VALUES_LENGTH).map { r -> GoldPrice(r.date, OUNCE * r.price) }
                else -> getCurrencyRates(code, HISTORIC_VALUES_LENGTH)
            }
            return createReport(code, rates, today)
        }

        fun formatZloty(amount: Double?): String {
            return when (amount) {
                null -> "----zł"
                in 10_000.0..100_000.0 -> String.format("%5.0f", amount)
                in 1000.0..10_000.0 -> String.format("%4.0fzł", amount)
                in 100.0..1000.0 -> String.format("%3.0f zł", amount)
                in 10.0..100.0 -> String.format("%4.1fzł", amount)
                in 0.0..10.0 -> String.format("%3.2fzł", amount)
                else -> " MUCH "
            }
        }
    }

    private val currencyKeys = listOf(NbpKey.currency1, NbpKey.currency2, NbpKey.currency3).apply {
        forEach {
            val code = config[it]
            check(code.length == 3) { "invalid currency code $code" }
        }
    }

    private var currentReport: CurrentReport? = null

    private suspend fun drawCurrencyInfo(cr: SingleCurrencyReport?, offset: Int, diffChartStep: Double) {
        require(diffChartStep > 0)
        hardware.frontDisplay.apply {
            setStaticText(offset, when (cr?.isLatestFromToday) {
                true -> cr.code.toUpperCase()
                false -> cr.code.toLowerCase()
                null -> "---"
            })
            setStaticText(offset + 20, formatZloty(cr?.latest))

            if (cr != null) {
                val unit = cr.latest * diffChartStep
                setOneLineDiffChart(5 * (offset + 3), cr.latest, cr.historical, unit)
            }
        }
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
        val report = currentReport

        if (redrawStatus) {
            val diffChartStep = config[NbpKey.diffChartFraction]
            logger.debug { "Refreshing NBP screen, diff chart step: $diffChartStep." }
            launch { drawCurrencyInfo(report?.currencies?.get(NbpKey.currency1), 0, diffChartStep) }
            launch { drawCurrencyInfo(report?.currencies?.get(NbpKey.currency2), 7, diffChartStep) }
            launch { drawCurrencyInfo(report?.currencies?.get(NbpKey.currency3), 14, diffChartStep) }
        }

        drawProgressBar(report?.timestamp)

        Unit
    }

    /**
     * Progress bar is dependent only on current time so always success.
     */
    override suspend fun poolInstantData(): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun poolStatusData(): UpdateStatus = coroutineScope {
        val now = LocalDateTime.now()


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