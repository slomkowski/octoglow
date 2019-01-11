package eu.slomkowski.octoglow

import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class GeigerTest {
    @ParameterizedTest
    @MethodSource("getI2CBus")
    fun testBrightness(bus: I2CBus) = runBlocking {
        val geiger = Geiger(bus)

        val x = async { geiger.setEyeEnabled(true) }

        x.await()

        for (i in 0..5) {
            geiger.setBrightness(i)
            delay(1000)
        }

        geiger.setBrightness(3)
    }
}