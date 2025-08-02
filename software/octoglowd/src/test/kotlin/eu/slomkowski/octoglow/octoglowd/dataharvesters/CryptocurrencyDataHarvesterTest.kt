package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.dataharvesters.CryptocurrencyDataHarvester.Companion.getLatestOhlc
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CryptocurrencyDataHarvesterTest {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Test
    fun testCoinpaprikaMethods() {
        runBlocking {
            getLatestOhlc("btc-bitcoin").apply {
                logger.info { "Cryptocurrency is $this." }
                assertNotNull(this)
                assertTrue(high > low)
                assertTrue(high > 0)
                assertTrue(low > 0)
            }
        }
    }
}