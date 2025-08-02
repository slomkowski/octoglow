@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.ConfCryptocurrencies
import eu.slomkowski.octoglow.octoglowd.DatabaseDemon
import eu.slomkowski.octoglow.octoglowd.defaultTestConfig
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


class CryptocurrencyViewTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }


    @Test
    fun testFormatDollars() {
        assertEquals("$1.764", CryptocurrencyView.formatDollars(1.764343))
        assertEquals("$43.23", CryptocurrencyView.formatDollars(43.229434343))
        assertEquals("$76.00", CryptocurrencyView.formatDollars(76.0))
        assertEquals("$232.3", CryptocurrencyView.formatDollars(232.29094))
        assertEquals("$3072", CryptocurrencyView.formatDollars(3072.23))
        assertEquals("$10345", CryptocurrencyView.formatDollars(10345.23323))
        assertEquals("$101k", CryptocurrencyView.formatDollars(100525.4321))
        assertEquals("$-----", CryptocurrencyView.formatDollars(null))
    }

    @Test
    fun testView() {
        val config = defaultTestConfig.copy(cryptocurrencies = ConfCryptocurrencies(coin1 = "BTC", coin2 = "ETH", coin3 = "EOS"))

        val db = mockk<DatabaseDemon>()
        val hardware = mockk<Hardware>()

        coEvery { hardware.frontDisplay.setUpperBar(any(), false) } just Runs
        coEvery { hardware.frontDisplay.setStaticText(any(), any()) } just Runs

        runBlocking {
            val view = CryptocurrencyView(config, db, hardware)

            view.redrawDisplay(true, true, Clock.System.now(), null, null)

            coVerify {
                hardware.frontDisplay.setStaticText(0, "---")
                hardware.frontDisplay.setStaticText(20, "$-----")

                hardware.frontDisplay.setStaticText(7, "---")
                hardware.frontDisplay.setStaticText(27, "$-----")

                hardware.frontDisplay.setStaticText(14, "---")
                hardware.frontDisplay.setStaticText(34, "$-----")
            }

            confirmVerified(hardware)
        }
    }
}
