package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.daemon.AnalogGaugeDaemon.Companion.parseProcNetWirelessFile
import eu.slomkowski.octoglow.octoglowd.hardware.DacChannel
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.Assert
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class AnalogGaugeDaemonTest {

    companion object : KLogging() {
        private const val DELTA = 0.01
    }

    @Test
    fun testBasic() {
        val hardware = mockk<Hardware>()
        val d = AnalogGaugeDaemon(mockk(), hardware)

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

    @Test
    fun testParseProcNetWirelessFile() {
        fun getList(t: String): List<AnalogGaugeDaemon.WifiSignalInfo> =
            javaClass.getResourceAsStream("/proc-net-wireless/$t").use { inputStream ->
                parseProcNetWirelessFile(BufferedReader(InputStreamReader(inputStream, StandardCharsets.US_ASCII)))
            }

        getList("1.txt").apply {
            Assert.assertEquals(1, size)
            get(0).apply {
                Assert.assertEquals("wlp3s0", ifName)
                Assert.assertEquals(68.57, linkQuality, DELTA)
                Assert.assertEquals(-62.0, signalStrength, DELTA)
            }
        }

        getList("2.txt").apply {
            assertTrue(isEmpty())
        }

        getList("3.txt").apply {
            Assert.assertEquals(1, size)
            get(0).apply {
                Assert.assertEquals("wlan0", ifName)
                Assert.assertEquals(84.29, linkQuality, DELTA)
                Assert.assertEquals(-51.0, signalStrength, DELTA)
            }
        }

        //todo more tests on different files
    }

}