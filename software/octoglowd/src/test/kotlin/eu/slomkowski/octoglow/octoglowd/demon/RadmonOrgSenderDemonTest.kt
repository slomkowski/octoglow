@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon

import eu.slomkowski.octoglow.octoglowd.demon.RadmonOrgSenderDemon.Companion.getLastReading
import eu.slomkowski.octoglow.octoglowd.demon.RadmonOrgSenderDemon.Companion.phpDateTimeFormat
import eu.slomkowski.octoglow.octoglowd.demon.RadmonOrgSenderDemon.Companion.submitToRadmonOrg
import eu.slomkowski.octoglow.octoglowd.testConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.format
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Percentage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class RadmonOrgSenderDemonTest {

    @Test
    fun testPhpDateTimeFormat() {
        assertThat(Instant.fromEpochMilliseconds(1752510415123).format(phpDateTimeFormat)).isEqualTo("2025-07-14 16:26:55")
    }

    @Test
    fun testSubmitToRadmonOrg(): Unit = runBlocking {
        val sentValue = 40.0 + Random.nextDouble(-10.0, 10.0)

        val cfg = testConfig.radmon
        if (cfg != null) {
            assertThat(cfg.enabled).isTrue()
            submitToRadmonOrg(
                cfg.username,
                cfg.password,
                Clock.System.now(),
                sentValue,
            )

            delay(10.seconds)

            val lastReading = getLastReading(cfg.username)
            assertThat(lastReading).isCloseTo(sentValue, Percentage.withPercentage(1.0))
        } else {
            fail("radmon.org config not enabled")
        }
    }
}