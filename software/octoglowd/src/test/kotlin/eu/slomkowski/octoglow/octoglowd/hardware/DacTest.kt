package eu.slomkowski.octoglow.octoglowd.hardware

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(HardwareParameterResolver::class)
class DacTest {

    @Test
    fun testOut2Max(hardware: Hardware) {
        testOut2(hardware, (255).toInt())
    }

    @Test
    fun testOut2Half(hardware: Hardware) {
        testOut2(hardware, (127).toInt())
    }

    @Test
    fun testOut2Zero(hardware: Hardware) {
        testOut2(hardware, 0)
    }

    private fun testOut2(hardware: Hardware, v: Int) = runBlocking {
        Dac(hardware).apply {
            setValue(DacChannel.C2, v)
            setValue(DacChannel.C1, v)
        }
    }
}