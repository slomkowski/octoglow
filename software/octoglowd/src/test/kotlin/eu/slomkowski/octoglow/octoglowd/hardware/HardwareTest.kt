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
import kotlin.time.ExperimentalTime

@ExperimentalTime
class HardwareTest {

    companion object : KLogging() {
        fun createRealHardware(): Hardware {
            val config = Config { addSpec(ConfKey) }.from.map.kv(
                mapOf("i2cBus" to testConfig[TestConfKey.i2cBus])
            )
            return Hardware(config)
        }
    }

    @Test
    fun testBrightness() {
        runBlocking {
            createRealHardware().use { hardware ->
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