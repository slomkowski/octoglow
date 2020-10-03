package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitObject
import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.csvDeserializerOf
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.coroutineScope
import mu.KLogging
import org.apache.commons.csv.CSVFormat
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

class StockView(
        private val config: Config,
        hardware: Hardware)
    : FrontDisplayView(hardware,
        "Warsaw Stock Exchange index",
        Duration.ofMinutes(15),
        Duration.ofSeconds(15),
        Duration.ofSeconds(13)) {


    data class StockInfoDto(
            val ticker: String,
            val interval: Duration,
            val timestamp: LocalDateTime,
            val open: BigDecimal,
            val high: BigDecimal,
            val low: BigDecimal,
            val close: BigDecimal,
            val volume: Int,
            val openInt: Int) {
        init {
            require(ticker.isNotBlank())
            require(interval > Duration.ZERO)
            require(high >= low)
            require(volume >= 0)
        }
    }

    companion object : KLogging() {
        private val cookie: String = UUID.randomUUID().toString()

        private val shortTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")

        private const val STOOQ_URL = "https://stooq.pl/db/d/"

        suspend fun downloadStockData(date: LocalDate): List<StockInfoDto> {
            val parameters = listOf(
                    "t" to "h",
                    "d" to date.format(DateTimeFormatter.BASIC_ISO_DATE),
                    "u" to cookie)

            logger.debug("Downloading stock data for $date from stooq.pl.")

            return Fuel.get(STOOQ_URL, parameters).awaitObject(csvDeserializerOf(CSVFormat.DEFAULT.withFirstRecordAsHeader()) { record ->
                val rowDate = LocalDate.parse(record[2], DateTimeFormatter.BASIC_ISO_DATE)
                val time = LocalTime.parse(record[3], shortTimeFormatter)
                StockInfoDto(
                        record.get(0),
                        Duration.ofMinutes(record[1].toLong()),
                        LocalDateTime.of(rowDate, time),
                        record[4].toBigDecimal(),
                        record[5].toBigDecimal(),
                        record[6].toBigDecimal(),
                        record[7].toBigDecimal(),
                        record[8].toInt(),
                        record[9].toInt())
            })
        }
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean) = coroutineScope {
        TODO()

        Unit
    }

    /**
     * Progress bar is dependent only on current time so always success.
     */
    override suspend fun poolInstantData(): UpdateStatus = UpdateStatus.FULL_SUCCESS

    override suspend fun poolStatusData(): UpdateStatus = coroutineScope {
        TODO()
    }
}