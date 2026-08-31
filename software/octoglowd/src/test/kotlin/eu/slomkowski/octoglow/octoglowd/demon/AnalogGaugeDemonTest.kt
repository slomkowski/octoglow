package eu.slomkowski.octoglow.octoglowd.demon

import eu.slomkowski.octoglow.octoglowd.demon.AnalogGaugeDemon.Companion.parseProcNetWirelessFile
import eu.slomkowski.octoglow.octoglowd.hardware.DacChannel
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.HardwareParameterResolver
import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds


@ExtendWith(HardwareParameterResolver::class)
class AnalogGaugeDemonTest {

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val DELTA = 0.01
    }

    @Test
    fun testBasic() {
        val hardware = mockk<Hardware>()
        val d = AnalogGaugeDemon(hardware)

        val dacValueSlot = slot<Int>()
        coEvery { hardware.dac.setValue(any(), capture(dacValueSlot)) } answers {
            assertThat(dacValueSlot.captured).isBetween(0, 255)
        }

        runBlocking {
            repeat(20) {
                d.poll()
            }
        }
    }

    @Test
    fun testBasicRealHardware(hardware: Hardware) {
        val d = AnalogGaugeDemon(hardware)

        runBlocking {
            repeat(20) {
                d.poll()
                delay(200.milliseconds)
            }
        }
    }

    @Test
    fun testSetValueClampsToDacRange() {
        val hardware = mockk<Hardware>()
        val d = AnalogGaugeDemon(hardware)

        val dacValues = mutableListOf<Int>()
        coEvery { hardware.dac.setValue(any(), capture(dacValues)) } answers { }

        val samplesToFillAveragingBuffer = 5

        runBlocking {
            // link quality above the assumed 70 maximum yields a fraction > 1.0; without clamping
            // the averaged value * 255 would exceed 255 and blow the DAC's 0..255 contract
            repeat(samplesToFillAveragingBuffer) { d.setValue(DacChannel.C1, 2.0) }
            // a negative fraction must clamp to the low end
            repeat(samplesToFillAveragingBuffer) { d.setValue(DacChannel.C2, -1.0) }
        }

        assertThat(dacValues).isNotEmpty
        assertThat(dacValues).allMatch { it in 0..255 }
        // after the averaging buffer fills, the clamped values reach the extremes exactly
        assertThat(dacValues.take(samplesToFillAveragingBuffer).last()).isEqualTo(255)
        assertThat(dacValues.last()).isEqualTo(0)
    }

    @Test
    fun testParseProcStat() {
        val agd = AnalogGaugeDemon(HardwareMock())

        fun getMeasurement(t: String): Double =
            javaClass.getResourceAsStream("/proc-stat/$t").use { inputStream ->
                agd.parseProcStatFile(BufferedReader(InputStreamReader(inputStream, StandardCharsets.US_ASCII)))
            }

        getMeasurement("1.txt").apply {
            assertThat(this).isNotNull
            assertThat(this).isCloseTo(0.08, within(DELTA))
        }

        getMeasurement("2.txt").apply {
            assertThat(this).isNotNull
            assertThat(this).isCloseTo(0.07, within(DELTA))

        }

        getMeasurement("2.txt").apply {
            assertThat(this).isNotNull
            assertThat(this).isEqualTo(0.0)
        }
    }

    @Test
    fun testParseProcNetWirelessFile() {
        fun getList(t: String): List<AnalogGaugeDemon.WifiSignalInfo> =
            javaClass.getResourceAsStream("/proc-net-wireless/$t").use { inputStream ->
                parseProcNetWirelessFile(BufferedReader(InputStreamReader(inputStream, StandardCharsets.US_ASCII)))
            }

        getList("1.txt").apply {
            assertThat(size).isEqualTo(1)
            get(0).apply {
                assertThat(ifName).isEqualTo("wlp3s0")
                assertThat(linkQuality).isCloseTo(68.57, within(DELTA))
                assertThat(signalStrength).isCloseTo(-62.0, within(DELTA))
            }
        }

        getList("2.txt").apply {
            assertThat(this).isEmpty()
        }

        getList("3.txt").apply {
            assertThat(size).isEqualTo(1)
            get(0).apply {
                assertThat(ifName).isEqualTo("wlan0")
                assertThat(linkQuality).isCloseTo(84.29, within(DELTA))
                assertThat(signalStrength).isCloseTo(-51.0, within(DELTA))
            }
        }

        //todo more tests on different files, z laptopa na przykład
    }

}