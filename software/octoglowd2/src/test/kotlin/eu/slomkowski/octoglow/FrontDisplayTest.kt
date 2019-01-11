package eu.slomkowski.octoglow

import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.system.measureTimeMillis

@ExtendWith(I2CBusParameterResolver::class)
class FrontDisplayTest {

    companion object : KLogging()

    @Test
    fun testSetUpperBar(i2CBus: I2CBus) {
        runBlocking {
            val frontDisplay = FrontDisplay(i2CBus)

            (0..19).forEach {
                frontDisplay.setUpperBar(listOf(it))
                delay(100)
            }

            frontDisplay.setUpperBar(arrayOf(
                    true, true, false, true, false,
                    false, false, false, false, false,
                    false, false, true, false, false,
                    false, false, false, false, true))

            delay(1000)

            frontDisplay.setUpperBar(listOf(0, 1, 19))

            delay(1000)

            frontDisplay.setUpperBar(listOf(0, 1, 19), true)
        }
    }

    @Test
    fun testSetStaticText(i2CBus: I2CBus) {
        runBlocking {
            FrontDisplay(i2CBus).apply {
                clear()
                setStaticText(0, "Foo bar text.")
                delay(1000)

                val t = measureTimeMillis {
                    setStaticText(1, "Znajdź pchły, wróżko! Film \"Teść\".")
                }
                logger.info { "Executed in $t ms." }
            }
        }
    }

    @Test
    fun testSetScrollingText(i2CBus: I2CBus) {
        runBlocking {
            FrontDisplay(i2CBus).apply {
                clear()
                setScrollingText(Slot.SLOT0, 34, 5,
                        "The quick brown fox jumps over the lazy dog. 20\u00B0C Dość gróźb fuzją, klnę, pych i małżeństw!")
            }
        }
    }
}