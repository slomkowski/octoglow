package eu.slomkowski.octoglow.octoglowd.hardware

import com.thedeanda.lorem.LoremIpsum
import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.TestConfKey
import eu.slomkowski.octoglow.octoglowd.testConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Test

class PhysicalHardwareTest {

    companion object : KLogging()

    @Test
    fun testBrightness() {
        val config = Config { addSpec(ConfKey) }
        config[ConfKey.i2cBus] = testConfig[TestConfKey.i2cBus]

        runBlocking {
            PhysicalHardware(config).use { hardware ->
                hardware.frontDisplay.setStaticText(0, LoremIpsum.getInstance().getWords(20).take(39))
                hardware.clockDisplay.setDisplay(12, 34, true, false)

                for (b in (0..5)) {
                    logger.info { "Setting brightness to $b." }
                    hardware.setBrightness(b)
                    delay(4_000)
                }

                delay(2000)
            }
        }
    }
}