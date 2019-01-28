package eu.slomkowski.octoglow.octoglowd.hardware

import com.thedeanda.lorem.LoremIpsum
import eu.slomkowski.octoglow.octoglowd.TestConfKey
import eu.slomkowski.octoglow.octoglowd.testConfig
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

 class HardwareTest {
    @Test
    fun testBrightness() {
        runBlocking {
            Hardware(testConfig[TestConfKey.i2cBus]).use { hardware ->
                hardware.frontDisplay.setStaticText(0, LoremIpsum.getInstance().getWords(5).substring(0, 38))
                hardware.clockDisplay.setDisplay(12, 34, true, false)

                for (b in (0..5)) {
                    hardware.setBrightness(b)
                    delay(1000)
                }
            }
        }
    }
}