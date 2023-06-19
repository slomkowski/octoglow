package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.DatabaseLayer
import eu.slomkowski.octoglow.octoglowd.Stock
import eu.slomkowski.octoglow.octoglowd.StocksKey
import eu.slomkowski.octoglow.octoglowd.WARSAW_ZONE_ID
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import mu.KLogging
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.StringReader
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@ExperimentalTime
class StockView(
    private val config: Config,
    private val database: DatabaseLayer,
    hardware: Hardware
) : FrontDisplayView(
    hardware,
    "Warsaw Stock Exchange index",
    1.minutes,//todo change to 20 min, stays 1 minute for dev
    15.seconds,
    1.seconds
) {

    data class StockInfoDto(
        val ticker: String,
        val interval: Duration,
        val timestamp: ZonedDateTime,
        val open: BigDecimal,
        val high: BigDecimal,
        val low: BigDecimal,
        val close: BigDecimal,
        val volume: Long,
        val openInt: Long
    ) {
        init {
            require(ticker.isNotBlank())
            require(interval > Duration.ZERO)
            require(high >= low)
            require(volume >= 0)
        }

        val typicalPrice: BigDecimal
            get() = (high + low + close) / BigDecimal(3)
    }

    data class SingleStockReport(
        val code: String,
        val isLatestFromToday: Boolean,
        val latest: Double,
        val historical: List<Double?>
    ) {
        init {
            require(historical.size == HISTORIC_VALUES_LENGTH)
        }
    }

    data class CurrentReport(
        val timestamp: Instant,
        val currencies: Map<String, SingleStockReport>
    )

    private var currentReport: CurrentReport? = null

    companion object : KLogging() {
        private const val HISTORIC_VALUES_LENGTH = 14

        private val cookie: String = UUID.randomUUID().toString()

        private val shortTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")

        private const val STOOQ_URL = "https://stooq.pl/db/d/"

        suspend fun downloadStockData(date: LocalDate): List<StockInfoDto> {
            TODO("reimplement to other stock provider")

            val parameters = listOf(
                "t" to "h",
                "d" to date.format(DateTimeFormatter.BASIC_ISO_DATE),
                "u" to cookie
            )

            logger.debug("Downloading stock data for $date from stooq.pl.")

            val responseString = eu.slomkowski.octoglow.octoglowd.httpClient.get<String>(STOOQ_URL)

            if (responseString.isBlank()) {
                throw IllegalStateException("response is empty")
            } else if (responseString.startsWith("Przekroczony dzienny limit wywolan", ignoreCase = true)) {
                throw IllegalStateException("too many requests for today")
            }

            return CSVParser(
                StringReader(responseString),
                CSVFormat.DEFAULT.withFirstRecordAsHeader()
            ).records.map { record ->
                val rowDate = LocalDate.parse(record[2], DateTimeFormatter.BASIC_ISO_DATE)
                val time = LocalTime.parse(record[3], shortTimeFormatter)
                StockInfoDto(
                    record.get(0),
                    Duration.ofMinutes(record[1].toLong()),
                    ZonedDateTime.of(rowDate, time, WARSAW_ZONE_ID),
                    record[4].toBigDecimal(),
                    record[5].toBigDecimal(),
                    record[6].toBigDecimal(),
                    record[7].toBigDecimal(),
                    record[8].toLong(),
                    record[9].toLong()
                )
            }
        }
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant) =
        coroutineScope {
            val fd = hardware.frontDisplay
            launch { fd.setStaticText(0, "stock view") }

            Unit
        }

    /**
     * Progress bar is dependent only on current time so always success.
     */
    override suspend fun poolInstantData(now: Instant): UpdateStatus = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {

        val tickers = config[StocksKey.tickers]
        require(tickers.isNotEmpty()) { "no tickets defined" }

        val stockData =
            downloadStockData(now.toLocalDateTime(TimeZone.currentSystemDefault()).toJavaLocalDateTime().toLocalDate())

        val newReport = CurrentReport(now, tickers.map { ticker ->
            require(ticker.isNotBlank()) { "ticker is blank" }
            async {
                try {
                    createSingleStockReport(stockData, now, ticker)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to update status on $ticker." }
                    null
                }?.let { ticker to it }
            }
        }.awaitAll().filterNotNull().toMap())

        currentReport = newReport

        when (newReport.currencies.size) {
            tickers.size -> UpdateStatus.FULL_SUCCESS
            0 -> UpdateStatus.FAILURE
            else -> UpdateStatus.PARTIAL_SUCCESS
        }
    }

    suspend fun createSingleStockReport(
        stockData: Collection<StockInfoDto>,
        ts: Instant,
        ticker: String
    ): SingleStockReport? {
        val dbKey = Stock(ticker)

        val newestToday = stockData.filter { it.ticker == ticker }.maxByOrNull { it.timestamp }

        if (newestToday != null) {
            database.insertHistoricalValueAsync(newestToday.timestamp, dbKey, newestToday.typicalPrice.toDouble())
        }

        val historical =
            database.getLastHistoricalValuesByHourAsync(ts, dbKey, HISTORIC_VALUES_LENGTH).await() //todo by day

        val latestPrice = newestToday?.typicalPrice?.toDouble() ?: historical.last()

        return latestPrice?.let {
            SingleStockReport(
                ticker,
                newestToday != null,
                latestPrice,
                historical
            )
        }
    }
}