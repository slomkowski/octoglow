package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.hardware.DacChannel
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CpuUsageIndicatorDaemonTest {

    @Test
    fun testBasic() {
        val hardware = mockk<Hardware>()
        val d = CpuUsageIndicatorDaemon(hardware)

        val dacValueSlot = slot<Int>()
        coEvery { hardware.dac.setValue(DacChannel.C2, capture(dacValueSlot)) } answers {
            assertTrue(dacValueSlot.captured in (0..255))
        }

        runBlocking {
            repeat(20) {
                d.pool()
            }
        }
    }
}