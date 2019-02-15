package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.hardware.Bme280.Companion.checkNot00andNotFF
import io.dvlopt.linux.i2c.I2CBuffer
import org.junit.jupiter.api.Test
import kotlin.test.assertFails

internal class Bme280Test {
    @Test
    fun testCheckNot00andNotFF() {
        assertFails {
            checkNot00andNotFF(byteArrayOf(0, 0, 0))
        }

//        assertFails {
//            checkNot00andNotFF(byteArrayOf(0xff, 0xff, 0xff))
//        }
//
//        checkNot00andNotFF(I2CBuffer(3)
//                .set(0, 0xff)
//                .set(1, 0xff))
    }
}