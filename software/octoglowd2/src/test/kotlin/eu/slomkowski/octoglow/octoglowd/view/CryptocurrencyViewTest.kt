package eu.slomkowski.octoglow.octoglowd.view

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CryptocurrencyViewTest {

    @Test
    fun testGetLatestOhlc() {
        runBlocking {
            CryptocurrencyView.getLatestOhlc("btc-bitcoin").apply {
                assertNotNull(this)
                assertTrue(high > low)
                assertTrue(high > 0)
                assertTrue(low > 0)
            }

            CryptocurrencyView.getCoins().apply {
                assertTrue(isNotEmpty())
                assertNotNull(this.find { it.symbol == "BTC" })
            }
        }
    }

    @Test
    fun testFormatDollars() {
        assertEquals("$1.764", CryptocurrencyView.formatDollars(1.764343))
        assertEquals("$43.23", CryptocurrencyView.formatDollars(43.229434343))
        assertEquals("$76.00", CryptocurrencyView.formatDollars(76.0))
        assertEquals("$232.3", CryptocurrencyView.formatDollars(232.29094))
        assertEquals("$3072", CryptocurrencyView.formatDollars(3072.23))
        assertEquals("$10345", CryptocurrencyView.formatDollars(10345.23323))
        assertEquals("$-----", CryptocurrencyView.formatDollars(null))
    }
}