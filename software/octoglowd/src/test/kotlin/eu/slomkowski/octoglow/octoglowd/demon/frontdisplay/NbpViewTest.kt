package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class NbpViewTest {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Test
    fun testFormatZloty() {
        assertEquals("1.76zł", NbpView.formatZloty(1.764343))
        assertEquals("43.2zł", NbpView.formatZloty(43.229434343))
        assertEquals("76.0zł", NbpView.formatZloty(76.0))
        assertEquals("232 zł", NbpView.formatZloty(232.29094))
        assertEquals("3072zł", NbpView.formatZloty(3072.23))
        assertEquals("10k3zł", NbpView.formatZloty(10345.23323))
        assertEquals("----zł", NbpView.formatZloty(null))
    }
}