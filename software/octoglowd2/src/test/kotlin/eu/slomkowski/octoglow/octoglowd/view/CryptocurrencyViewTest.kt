package eu.slomkowski.octoglow.octoglowd.view

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}