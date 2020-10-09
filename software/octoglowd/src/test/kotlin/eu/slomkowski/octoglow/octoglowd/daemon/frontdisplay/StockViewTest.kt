package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertNotNull

internal class StockViewTest {

    companion object : KLogging()

    @Test
    fun testDownloadStockData() {
        val lastWeekDay = checkNotNull(object : Iterator<LocalDate> {
            var d = LocalDate.now().minusDays(7)

            override fun hasNext() = d <= LocalDate.now()

            override fun next(): LocalDate {
                d = d.plusDays(1)
                return d
            }
        }.asSequence().findLast { it.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) })

        val stockData = runBlocking { StockView.downloadStockData(lastWeekDay) }
        assertTrue(stockData.isNotEmpty())
        logger.info("Downloaded {} stocks.", stockData.size)

        val stockDataByTicker = stockData.groupBy { it.ticker }
        logger.info("Found {} tickers.", stockDataByTicker.size)

        assertNotNull(stockDataByTicker["CDR"])
        assertNotNull(stockDataByTicker["TPE"])
        assertNotNull(stockDataByTicker["PKO"])
    }
}