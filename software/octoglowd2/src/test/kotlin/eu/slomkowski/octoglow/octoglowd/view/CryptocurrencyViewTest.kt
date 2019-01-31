package eu.slomkowski.octoglow.octoglowd.view

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.CryptocurrenciesKey
import eu.slomkowski.octoglow.octoglowd.hardware.MockHardware
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CryptocurrencyViewTest {

    @Test
    fun testCoinpaprikaMethods() {
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

    @Test
    fun testView() {
        val config = Config {
            addSpec(CryptocurrenciesKey)
            set(CryptocurrenciesKey.coin1, "BTC")
            set(CryptocurrenciesKey.coin2, "ETH")
            set(CryptocurrenciesKey.coin3, "EOS")

        }
        val hardware = MockHardware()

        runBlocking {
            coEvery { hardware.frontDisplay.setStaticText(any(), any()) } just Runs

            val view = CryptocurrencyView(config, mockk(), hardware)

            view.redrawDisplay()

            coVerify {
                hardware.frontDisplay.setStaticText(0, "---") }
        }
    }
}