package eu.slomkowski.octoglow.octoglowd.daemon

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.assertEquals

class BrightnessDaemonTest {
    companion object : KLogging()

    @Test
    fun testCalculateBrightnessFraction() {
        val hardwareMock = mockk<Hardware>()
        val databaseMock = mockk<DatabaseLayer>()

        coEvery { hardwareMock.setBrightness(any()) } just Runs
        coEvery { databaseMock.getChangeableSettingAsync(ChangeableSetting.BRIGHTNESS) } returns CompletableDeferred("AUTO")
        coEvery { databaseMock.setChangeableSettingAsync(ChangeableSetting.BRIGHTNESS, any()) } returns Job()

        val bd = BrightnessDaemon(Config {
            addSpec(SleepKey)
            addSpec(GeoPosKey)
            set(SleepKey.startAt, LocalTime.of(0, 45))
            set(SleepKey.duration, Duration.ofHours(9))
            poznanCoordinates.let { (lat, lng) ->
                set(GeoPosKey.latitude, lat)
                set(GeoPosKey.longitude, lng)
            }
        }, databaseMock, hardwareMock)

        assertEquals(3, bd.calculateBrightnessFraction(ZonedDateTime.of(LocalDateTime.of(2019, 1, 23, 17, 21), WARSAW_ZONE_ID)))
        assertEquals(4, bd.calculateBrightnessFraction(ZonedDateTime.of(LocalDateTime.of(2019, 1, 23, 8, 21), WARSAW_ZONE_ID)))

        runBlocking {
            bd.pool()

            bd.setForcedMode(3)

            bd.pool()

            coVerify(exactly = 1) { databaseMock.setChangeableSettingAsync(ChangeableSetting.BRIGHTNESS, "3") }
            coVerify(exactly = 2) { hardwareMock.setBrightness(3) }
        }
    }

    @Test
    fun testCalculateFromData() {
        fun cr(sleepTime: LocalTime, sleepDurationHours: Int, time: LocalTime) = BrightnessDaemon.calculateFromData(LocalTime.of(7, 32), LocalTime.of(17, 23),
                sleepTime, Duration.ofHours(sleepDurationHours.toLong()), time)

        (LocalTime.of(23, 31) to 8).let { (st, d) ->
            assertEquals(5, cr(st, d, LocalTime.of(12, 34)))
            assertEquals(1, cr(st, d, LocalTime.of(0, 0)))
            assertEquals(1, cr(st, d, LocalTime.of(0, 1)))
            assertEquals(1, cr(st, d, LocalTime.of(0, 2)))
            assertEquals(1, cr(st, d, LocalTime.of(3, 34)))
            assertEquals(1, cr(st, d, LocalTime.of(5, 34)))
            assertEquals(1, cr(st, d, LocalTime.of(5, 36)))
            assertEquals(1, cr(st, d, LocalTime.of(6, 12)))

            assertEquals(5, cr(st, d, LocalTime.of(9, 0)))
            assertEquals(3, cr(st, d, LocalTime.of(19, 3)))
            assertEquals(3, cr(st, d, LocalTime.of(23, 30)))

            testForWholeDay { cr(st, d, it) }
        }

        (LocalTime.of(0, 35) to 2).let { (st, d) ->
            assertEquals(5, cr(st, d, LocalTime.of(12, 34)))
            assertEquals(3, cr(st, d, LocalTime.of(0, 0)))
            assertEquals(1, cr(st, d, LocalTime.of(1, 34)))
            assertEquals(3, cr(st, d, LocalTime.of(5, 34)))

            assertEquals(5, cr(st, d, LocalTime.of(9, 0)))
            assertEquals(3, cr(st, d, LocalTime.of(19, 3)))
            assertEquals(3, cr(st, d, LocalTime.of(23, 30)))

            testForWholeDay { cr(st, d, it) }
        }

        (LocalTime.of(3, 20) to 9).let { (st, d) ->
            assertEquals(5, cr(st, d, LocalTime.of(12, 34)))
            assertEquals(3, cr(st, d, LocalTime.of(0, 0)))
            assertEquals(3, cr(st, d, LocalTime.of(1, 34)))
            assertEquals(1, cr(st, d, LocalTime.of(5, 34)))

            assertEquals(4, cr(st, d, LocalTime.of(9, 0)))
            assertEquals(3, cr(st, d, LocalTime.of(19, 3)))
            assertEquals(3, cr(st, d, LocalTime.of(23, 30)))

            testForWholeDay { cr(st, d, it) }
        }
    }

    private fun testForWholeDay(underTest: (LocalTime) -> Unit) {
        (0 until 24 * 60 * 60).map { LocalTime.ofSecondOfDay(it.toLong()) }.forEach {
            underTest(it)
        }
    }
}