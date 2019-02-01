package eu.slomkowski.octoglow.octoglowd.hardware

import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(I2CBusParameterResolver::class)
class GeigerTest {

    @Test
    fun testSetUpperBar(bus: I2CBus) {
        runBlocking {
            val geiger = Geiger(coroutineContext, bus)

            val x = async { geiger.setEyeEnabled(true) }

            x.await()

            for (i in 0..5) {
                geiger.setBrightness(i)
                delay(1000)
            }

            geiger.setBrightness(3)
        }
    }
}