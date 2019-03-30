package eu.slomkowski.octoglow.octoglowd.daemon

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.GeoPosKey
import eu.slomkowski.octoglow.octoglowd.SleepKey
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.poznanCoordinates
import io.mockk.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals

class BrightnessDaemonTest {
    companion object : KLogging()

    @Test
    fun testCalculateBrightnessFraction() {
        val hardwareMock = mockk<Hardware>()
        val bd = BrightnessDaemon(Config {
            addSpec(SleepKey)
            addSpec(GeoPosKey)
            set(SleepKey.startAt, LocalTime.of(0, 45))
            set(SleepKey.duration, Duration.ofHours(9))
            poznanCoordinates.let { (lat, lng) ->
                set(GeoPosKey.latitude, lat)
                set(GeoPosKey.longitude, lng)
            }
        }, mockk(), hardwareMock)

        assertEquals(3, bd.calculateBrightnessFraction(LocalDateTime.of(2019, 1, 23, 17, 21)))
        assertEquals(4, bd.calculateBrightnessFraction(LocalDateTime.of(2019, 1, 23, 8, 21)))

        runBlocking {
            coEvery { hardwareMock.setBrightness(any()) } just Runs

            bd.pool()

            coVerify(exactly = 1) { hardwareMock.setBrightness(any()) }
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