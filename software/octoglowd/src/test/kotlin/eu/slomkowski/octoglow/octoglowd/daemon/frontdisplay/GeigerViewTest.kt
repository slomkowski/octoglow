package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.now
import eu.slomkowski.octoglow.octoglowd.testConfig
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes


class GeigerViewTest {


    @Test
    fun testFormat() {
        assertEquals("---V", GeigerView.formatVoltage(null))
        assertEquals("134V", GeigerView.formatVoltage(134.4323))
        assertEquals(" 22V", GeigerView.formatVoltage(21.89323))

        assertEquals("-- CPM", GeigerView.formatCPM(null))
        assertEquals("24 CPM", GeigerView.formatCPM(23.8))
        assertEquals(" 1 CPM", GeigerView.formatCPM(1.3))

        assertEquals("-.-- uSv/h", GeigerView.formatUSVh(null))
        assertEquals("0.12 uSv/h", GeigerView.formatUSVh(0.123))
        assertEquals("0.02 uSv/h", GeigerView.formatUSVh(0.02))
    }

    @Test
    fun testCalculate() {
        assertEquals(0.108, GeigerView.calculateUSVh(81, 5.minutes), 0.001)
    }

    @Test
    fun testPhpDateTimeFormat() {
        assertThat(Instant.fromEpochMilliseconds(1752510415123).format(GeigerView.phpDateTimeFormat)).isEqualTo("2025-07-14 16:26:55")
    }

    @Test
    fun testSubmitToRadmonOrg(): Unit = runBlocking {
        val sentValue = 40.0 + Random.nextDouble(-10.0, 10.0)

        val cfg = testConfig.radmon
        if (cfg != null) {
            assertThat(cfg.enabled).isTrue()
            GeigerView.submitToRadmonOrg(
                cfg.username,
                cfg.password,
                now(),
                sentValue,
            )

            val lastReading = GeigerView.getLastReading(cfg.username)
            assertThat(lastReading).isCloseTo(sentValue, Percentage.withPercentage(0.01))
        } else {
            fail("radmon.org config not enabled")
        }
    }
}