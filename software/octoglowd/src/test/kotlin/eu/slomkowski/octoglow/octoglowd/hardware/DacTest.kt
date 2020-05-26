package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.daemon.CpuUsageIndicatorDaemon
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(I2CBusParameterResolver::class)
class DacTest {

    companion object : KLogging()

    @Test
    fun testOut2Max(i2CBus: I2CBus) {
        testOut2(i2CBus, (255 * CpuUsageIndicatorDaemon.CORRECTION_FACTOR).toInt())
    }

    @Test
    fun testOut2Half(i2CBus: I2CBus) {
        testOut2(i2CBus, (127 * CpuUsageIndicatorDaemon.CORRECTION_FACTOR).toInt())
    }

    @Test
    fun testOut2Zero(i2CBus: I2CBus) {
        testOut2(i2CBus, 0)
    }

    private fun testOut2(i2CBus: I2CBus, v: Int) = runBlocking {
        Dac(coroutineContext, i2CBus).apply {
            setValue(DacChannel.C2, v)
        }
    }
}