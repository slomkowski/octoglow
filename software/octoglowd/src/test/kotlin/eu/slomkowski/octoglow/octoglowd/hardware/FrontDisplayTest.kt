package eu.slomkowski.octoglow.octoglowd.hardware

import com.thedeanda.lorem.LoremIpsum
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
@ExtendWith(HardwareParameterResolver::class)
class FrontDisplayTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    @Test
    fun testSetUpperBar(hardware: Hardware): Unit = runBlocking {

        (0..19).forEach {
            hardware.frontDisplay.setUpperBar(listOf(it))
            delay(100)
        }

        hardware.frontDisplay.setUpperBar(
            booleanArrayOf(
                true, true, false, true, false,
                false, false, false, false, false,
                false, false, true, false, false,
                false, false, false, false, true
            )
        )

        delay(1000)

        hardware.frontDisplay.setUpperBar(listOf(0, 1, 19))

        delay(1000)

        hardware.frontDisplay.setUpperBar(listOf(0, 1, 19), true)
    }

    @Test
    fun testSetStaticText(hardware: Hardware): Unit = runBlocking {
        hardware.frontDisplay.apply {
            clear()
            setStaticText(0, "Foo bar text.")
            delay(1000)

            val t = measureTimeMillis {
                setStaticText(1, "Znajdź pchły, wróżko! Film \"Teść\".")
            }
            logger.info { "Executed in $t ms." }
        }
    }

    @Test
    fun testSetScrollingText(hardware: Hardware): Unit = runBlocking {
        hardware.frontDisplay.apply {
            clear()
            setScrollingText(
                Slot.SLOT0, 34, 5,
                "The quick brown fox jumps over the lazy dog. 20\u00B0C Dość gróźb fuzją, klnę, pych i małżeństw!"
            )
        }
    }

    @Test
    fun testSetBrightness(hardware: Hardware): Unit = runBlocking {
        hardware.frontDisplay.setStaticText(0, LoremIpsum.getInstance().getTitle(10).substring(0..39))

        for (i in 0..5) {
            hardware.frontDisplay.setBrightness(i)
            delay(1000)
        }
    }

    @Test
    @Disabled("only to test correct dial behavior")
    fun testGetButtonReport2(hardware: Hardware): Unit = runBlocking {

        repeat(1000) {
            hardware.frontDisplay.getButtonReport().apply {
                if (encoderDelta != 0) {
                    logger.info { "Delta: $encoderDelta." }
                }
            }
            delay(20)
        }
    }

    @Test
    fun testGetButtonReport(hardware: Hardware): Unit = runBlocking {
        hardware.frontDisplay.apply {
            val report1 = getButtonReport()
            logger.info { "Buttons 1: $report1" }
            val report2 = getButtonReport()
            logger.info { "Buttons 2: $report2" }

            assertEquals(ButtonState.NO_CHANGE, report2.button)
            assertEquals(0, report2.encoderDelta)
        }
    }

    @Test
    fun testCharts(hardware: Hardware): Unit = runBlocking {
        hardware.frontDisplay.apply {
            clear()
            setOneLineDiffChart(5 * 23, 4, listOf(0, 1, 2, 3, 4, 5, 6, 7, 6, 5), 1)
            setOneLineDiffChart(5 * 26, 68.2, listOf(16.0, 21.0, 84.3, 152.0, 79.6, 61.3), 31.5)
            setTwoLinesDiffChart(
                5 * 13,
                14,
                listOf(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 26, 24, 22, 20, 18, 16, 14),
                2
            )
            delay(2000)

            setOneLineDiffChart(5 * 23, 4, listOf(0, 1, null, null, null, 5, 6, 7, 6, 5), 1)
            setOneLineDiffChart(5 * 26, 68.2, listOf(16.0, null, 84.3, 152.0, 79.6, 61.3), 31.5)
            setTwoLinesDiffChart(
                5 * 13,
                14,
                listOf(0, null, null, 6, 8, 10, null, 14, 16, 18, 20, 22, 24, 26, 28, 26, 24, 22, 20, 18, 16, 14),
                2
            )
        }
    }
}