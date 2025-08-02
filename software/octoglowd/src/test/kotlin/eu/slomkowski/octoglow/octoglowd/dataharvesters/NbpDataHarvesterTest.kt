package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.dataharvesters.NbpDataHarvester.Companion.createReport
import eu.slomkowski.octoglow.octoglowd.dataharvesters.NbpDataHarvester.Companion.getCurrencyRates
import eu.slomkowski.octoglow.octoglowd.dataharvesters.NbpDataHarvester.Companion.getGoldRates
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NbpDataHarvesterTest {

    @Test
    fun testDownloadExchangeRates() {
        runBlocking {
            getCurrencyRates("EUR", 10).apply {
                assertNotNull(this)
                forEach { assertTrue { it.no.contains("NBP") } }
            }
        }
    }

    @Test
    fun testDownloadGoldPrice() {
        runBlocking {
            getGoldRates(10).apply {
                assertNotNull(this)
                assertTrue { isNotEmpty() }
                forEach { assertTrue { it.price > 0 } }
            }
        }
    }

    @Test
    fun testCreateReport1() {
        val rates = listOf(
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 12), 123.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 13), 124.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 14), 125.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 15), 126.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 16), 127.4)
        )
        val today = LocalDate(2019, 4, 19)

        val report = createReport("TEST", rates, today)
        assertEquals(14, report.historical?.size)
        assertEquals("TEST", report.code)
        assertEquals(127.4, report.value.getOrThrow())
        assertThat(report.isLatestFromToday).isFalse()
    }

    @Test
    fun testCreateReport2() {
        val rates = listOf(
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 12), 123.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 13), 124.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 14), 125.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 15), 126.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 16), 127.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 17), 128.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 18), 129.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 19), 130.4)
        )
        val today = LocalDate(2019, 4, 19)

        val report = createReport("TEST", rates, today)
        assertEquals(14, report.historical?.size)
        assertEquals("TEST", report.code)
        assertEquals(130.4, report.value.getOrThrow())
        assertThat(report.isLatestFromToday).isTrue()
    }

    @Test
    fun testCreateReport3() {
        val rates = listOf(
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 12), 123.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 13), 124.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 14), 125.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 15), 126.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 16), 127.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 17), 128.4),
            NbpDataHarvester.GoldPrice(LocalDate(2019, 4, 18), 129.4)
        )
        val today = LocalDate(2019, 4, 19)

        val report = createReport("TEST", rates, today)
        assertEquals(14, report.historical?.size)
        assertEquals("TEST", report.code)
        assertEquals(129.4, report.value.getOrThrow())
        assertThat(report.isLatestFromToday).isFalse()
    }
}