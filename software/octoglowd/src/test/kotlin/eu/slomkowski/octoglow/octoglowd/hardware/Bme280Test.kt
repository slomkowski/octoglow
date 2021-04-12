package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.hardware.Bme280.Companion.checkNot00andNotFF
import io.dvlopt.linux.i2c.I2CBuffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.Assert.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExtendWith(HardwareParameterResolver::class)
class Bme280Test {
    companion object : KLogging()

    @Test
    fun testGetMeanSeaLevelPressure() {
        assertEquals(1018.14, LocalSensorReport(22.0, 0.0, 1017.9).getMeanSeaLevelPressure(2.0), 0.01)
        assertEquals(1027.25, LocalSensorReport(22.0, 0.0, 1017.9).getMeanSeaLevelPressure(79.0), 0.01)
    }

    @Test
    fun testCheckNot00andNotFF() {
        assertFails {
            checkNot00andNotFF(I2CBuffer(3).set(0, 0).set(1, 0).set(2, 0))
        }

        assertFails {
            checkNot00andNotFF(I2CBuffer(3).set(0, 255).set(1, 255).set(2, 255))
        }

        checkNot00andNotFF(
            I2CBuffer(3)
                .set(0, 0xff)
                .set(1, 0xff)
        )
    }

    @Test
    fun testInitAndRead(hardware: Hardware) {
        runBlocking {
            Bme280(hardware).use { bme280 ->
                assertNotNull(bme280)
                bme280.initDevice()

                delay(1_000)

                repeat(15) {
                    val report = bme280.readReport()
                    logger.info { "Indoor report: $report." }
                    delay(500)
                }
            }
        }
    }
}