package eu.slomkowski.octoglow.octoglowd.hardware

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

class I2CDeviceTest {

    @Test
    fun testHandleI2cException() {
        assertEquals("native I2C transaction error: ENXIO: No such device or address",
                I2CDevice.handleI2cException(IOException("Native error while doing an I2C transaction : errno 6")).message)

        assertEquals("native I2C transaction error: errno 54534",
                I2CDevice.handleI2cException(IOException("Native error while doing an I2C transaction : errno 54534")).message)

        assertEquals("some other error",
                I2CDevice.handleI2cException(IOException("some other error")).message)
    }
}