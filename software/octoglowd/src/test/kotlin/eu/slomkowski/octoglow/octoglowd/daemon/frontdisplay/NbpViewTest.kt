package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NbpViewTest {
    companion object : KLogging()

    @Test
    fun testDownloadExchangeRates() {
        runBlocking {
            NbpView.getCurrencyRates("EUR", 10).apply {
                assertNotNull(this)
                forEach { assertTrue { it.no.contains("NBP") } }
            }
        }
    }

    @Test
    fun testDownloadGoldPrice() {
        runBlocking {
            NbpView.getGoldRates(10).apply {
                assertNotNull(this)
                assertTrue { isNotEmpty() }
                forEach { assertTrue { it.price > 0 } }
            }
        }
    }

    @Test
    fun testCreateReport1() {
        val rates = listOf(
                NbpView.GoldPrice(LocalDate.of(2019, 4, 12), 123.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 13), 124.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 14), 125.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 15), 126.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 16), 127.4))
        val today = LocalDate.of(2019, 4, 19)

        val report = NbpView.createReport("TEST", rates, today)
        assertEquals(14, report.historical.size)
        assertEquals("TEST", report.code)
        assertEquals(127.4, report.latest)
        assertFalse(report.isLatestFromToday)
    }

    @Test
    fun testCreateReport2() {
        val rates = listOf(
                NbpView.GoldPrice(LocalDate.of(2019, 4, 12), 123.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 13), 124.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 14), 125.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 15), 126.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 16), 127.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 17), 128.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 18), 129.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 19), 130.4))
        val today = LocalDate.of(2019, 4, 19)

        val report = NbpView.createReport("TEST", rates, today)
        assertEquals(14, report.historical.size)
        assertEquals("TEST", report.code)
        assertEquals(130.4, report.latest)
        assertTrue(report.isLatestFromToday)
    }

    @Test
    fun testCreateReport3() {
        val rates = listOf(
                NbpView.GoldPrice(LocalDate.of(2019, 4, 12), 123.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 13), 124.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 14), 125.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 15), 126.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 16), 127.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 17), 128.4),
                NbpView.GoldPrice(LocalDate.of(2019, 4, 18), 129.4))
        val today = LocalDate.of(2019, 4, 19)

        val report = NbpView.createReport("TEST", rates, today)
        assertEquals(14, report.historical.size)
        assertEquals("TEST", report.code)
        assertEquals(129.4, report.latest)
        assertFalse(report.isLatestFromToday)
    }

    @Test
    fun testFormatZloty() {
        assertEquals("1.76zł", NbpView.formatZloty(1.764343))
        assertEquals("43.2zł", NbpView.formatZloty(43.229434343))
        assertEquals("76.0zł", NbpView.formatZloty(76.0))
        assertEquals("232 zł", NbpView.formatZloty(232.29094))
        assertEquals("3072zł", NbpView.formatZloty(3072.23))
        assertEquals("10345", NbpView.formatZloty(10345.23323))
        assertEquals("----zł", NbpView.formatZloty(null))
    }
}