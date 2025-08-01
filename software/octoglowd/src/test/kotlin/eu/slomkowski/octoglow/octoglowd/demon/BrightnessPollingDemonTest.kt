package eu.slomkowski.octoglow.octoglowd.demon


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.demon.BrightnessDemon.Companion.isSleeping
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours


class BrightnessPollingDemonTest {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Test
    fun testCalculateBrightnessFraction() {
        val hardwareMock = mockk<Hardware>()
        val databaseMock = mockk<DatabaseDemon>()

        coEvery { hardwareMock.setBrightness(any()) } just Runs
        coEvery { databaseMock.getChangeableSettingAsync(ChangeableSetting.BRIGHTNESS) } returns CompletableDeferred("AUTO")
        coEvery { databaseMock.setChangeableSettingAsync(ChangeableSetting.BRIGHTNESS, any()) } returns Job()

        val bd = BrightnessDemon(
            defaultTestConfig.copy(
                sleep = ConfSleep(
                    LocalTime(0, 45),
                    9.hours
                )
            ), databaseMock, hardwareMock
        )

        assertEquals(
            3,
            bd.calculateBrightnessFraction(kotlinx.datetime.LocalDateTime(2019, 1, 23, 17, 21).toInstant(WARSAW_ZONE_ID), BrightnessDemon.LightSensor.INTERMEDIATE)
        )
        assertEquals(
            4,
            bd.calculateBrightnessFraction(kotlinx.datetime.LocalDateTime(2019, 1, 23, 8, 21).toInstant(WARSAW_ZONE_ID), BrightnessDemon.LightSensor.FULLY_LIGHT)
        )

        runBlocking {
            bd.poll()

            bd.setForcedMode(3)

            bd.poll()

            coVerify(exactly = 1) { databaseMock.setChangeableSettingAsync(ChangeableSetting.BRIGHTNESS, "3") }
            coVerify(exactly = 2) { hardwareMock.setBrightness(3) }
        }
    }

    @Test
    fun testIsSleeping() {
        (LocalTime(22, 0) to 8.hours).let { (s, d) ->
            assertTrue { isSleeping(s, d, LocalTime(23, 0)) }
            assertFalse { isSleeping(s, d, LocalTime(12, 0)) }
            assertTrue { isSleeping(s, d, LocalTime(3, 0)) }
            assertFalse { isSleeping(s, d, LocalTime(21, 0)) }
        }

        (LocalTime(1, 20) to 7.hours).let { (s, d) ->
            assertFalse { isSleeping(s, d, LocalTime(23, 0)) }
            assertFalse { isSleeping(s, d, LocalTime(12, 0)) }
            assertTrue { isSleeping(s, d, LocalTime(3, 0)) }
            assertTrue { isSleeping(s, d, LocalTime(7, 0)) }
            assertFalse { isSleeping(s, d, LocalTime(21, 0)) }
        }
    }

    @Test
    fun testCalculateFromData() {
        fun cr(sleepTime: LocalTime, sleepDurationHours: Int, time: LocalTime) = BrightnessDemon.calculateFromData(
            LocalTime(7, 32), LocalTime(17, 23),
            sleepTime, sleepDurationHours.hours, time, BrightnessDemon.LightSensor.INTERMEDIATE,
        )

        (LocalTime(23, 31) to 8).let { (st, d) ->
            assertEquals(5, cr(st, d, LocalTime(12, 34)))
            assertEquals(1, cr(st, d, LocalTime(0, 0)))
            assertEquals(1, cr(st, d, LocalTime(0, 1)))
            assertEquals(1, cr(st, d, LocalTime(0, 2)))
            assertEquals(1, cr(st, d, LocalTime(3, 34)))
            assertEquals(1, cr(st, d, LocalTime(5, 34)))
            assertEquals(1, cr(st, d, LocalTime(5, 36)))
            assertEquals(1, cr(st, d, LocalTime(6, 12)))

            assertEquals(5, cr(st, d, LocalTime(9, 0)))
            assertEquals(3, cr(st, d, LocalTime(19, 3)))
            assertEquals(3, cr(st, d, LocalTime(23, 30)))

            testForWholeDay { cr(st, d, it) }
        }

        (LocalTime(0, 35) to 2).let { (st, d) ->
            assertEquals(5, cr(st, d, LocalTime(12, 34)))
            assertEquals(3, cr(st, d, LocalTime(0, 0)))
            assertEquals(1, cr(st, d, LocalTime(1, 34)))
            assertEquals(3, cr(st, d, LocalTime(5, 34)))

            assertEquals(5, cr(st, d, LocalTime(9, 0)))
            assertEquals(3, cr(st, d, LocalTime(19, 3)))
            assertEquals(3, cr(st, d, LocalTime(23, 30)))

            testForWholeDay { cr(st, d, it) }
        }

        (LocalTime(3, 20) to 9).let { (st, d) ->
            assertEquals(5, cr(st, d, LocalTime(12, 34)))
            assertEquals(3, cr(st, d, LocalTime(0, 0)))
            assertEquals(3, cr(st, d, LocalTime(1, 34)))
            assertEquals(1, cr(st, d, LocalTime(5, 34)))

            assertEquals(4, cr(st, d, LocalTime(9, 0)))
            assertEquals(3, cr(st, d, LocalTime(19, 3)))
            assertEquals(3, cr(st, d, LocalTime(23, 30)))

            testForWholeDay { cr(st, d, it) }
        }
    }

    private fun testForWholeDay(underTest: (LocalTime) -> Unit) {
        (0 until 24 * 60 * 60).map { LocalTime.fromSecondOfDay(it) }.forEach {
            underTest(it)
        }
    }
}