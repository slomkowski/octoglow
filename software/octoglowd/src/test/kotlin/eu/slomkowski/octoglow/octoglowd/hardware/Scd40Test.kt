package eu.slomkowski.octoglow.octoglowd.hardware

import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertFails

@ExtendWith(HardwareParameterResolver::class)
class Scd40Test {

    private val logger = KotlinLogging.logger { }

    @Test
    fun `test readSerialNumber from mock`(): Unit = runBlocking {
        val hardwareMock = mockk<Hardware>()
        val scd40 = spyk(Scd40(hardwareMock))

        val mockedBuffer = intArrayOf(0x4c, 0x02, 0xde, 0xe3, 0x07, 0x4d, 0x3b, 0xe8, 0x51)
        coEvery { scd40.doTransaction(any<IntArray>(), any()) } returns mockedBuffer

        val result = scd40.readSerialNumber()
        assertThat(result).isEqualTo(0x4c02e3073be8)
    }

    @Test
    fun testPerformSelfTest(hardware: Hardware): Unit = runBlocking {
        val d = hardware.scd40
        d.stopPeriodicMeasurement()
        d.performSelfTest()
        d.startPeriodicMeasurement()
    }

    @Test
    fun testInitAndRead(hardware: Hardware): Unit = runBlocking {

        suspend fun readAndOutputReport() {
            val report = hardware.scd40.readMeasurementWithWaiting()
            logger.info { "Read report: $report" }
        }

        repeat(5) { readAndOutputReport() }

        hardware.scd40.setAmbientPressure(1000.0)
        repeat(2) { readAndOutputReport() }

        hardware.scd40.setAmbientPressure(hardware.bme280.readReport().realPressure)
        repeat(2) { readAndOutputReport() }
    }

    @Test
    fun testParse() {
        Scd40measurements.parse(intArrayOf(429, 27607, 28842)).apply {
            assertThat(temperature).isCloseTo(28.72, within(0.1))
            assertThat(humidity).isCloseTo(44.0, within(0.1))
            assertThat(co2).isCloseTo(429.0, within(0.1))
        }
    }

    @Test
    fun `test parse with different values`() {
        Scd40measurements.parse(intArrayOf(857, 27537, 29868)).apply {
            assertThat(temperature).isCloseTo(28.53, within(0.01))
            assertThat(humidity).isCloseTo(45.57, within(0.01))
            assertThat(co2).isCloseTo(857.0, within(0.01))
        }
    }

    @Test
    fun `test parse with zero values`() {
        assertThrows<IllegalArgumentException> {
            Scd40measurements.parse(intArrayOf(0, 0, 0))
        }
    }

    @Test
    fun `test parse with invalid array size`() {
        assertThrows<IllegalArgumentException> {
            Scd40measurements.parse(intArrayOf(429, 27607))
        }
    }

    @Test
    fun testTransaction(): Unit = runBlocking {
        val mockHardware = mockk<Hardware>()
        val scd40 = spyk(Scd40(mockHardware))

        coEvery { scd40.doTransaction(eq(intArrayOf(228, 184)), eq(3)) } returns intArrayOf(128, 0, 162)
        assertThat(scd40.getDataReadyStatus()).isFalse()

        coEvery { scd40.doTransaction(eq(intArrayOf(228, 184)), eq(3)) } returns intArrayOf(128, 6, 4)
        assertThat(scd40.getDataReadyStatus()).isTrue()

        coEvery { scd40.doTransaction(eq(intArrayOf(236, 5)), eq(9)) } returns intArrayOf(2, 10, 131, 107, 206, 150, 114, 241, 208)
        val measurement = scd40.readMeasurement()
        assertThat(measurement.co2).isCloseTo(522.0, within(0.1))
        assertThat(measurement.temperature).isCloseTo(28.69, within(0.1))
        assertThat(measurement.humidity).isCloseTo(44.89, within(0.1))

        coEvery { scd40.doTransaction(eq(intArrayOf(228, 184)), eq(3)) } returns intArrayOf(129, 6, 4) // failed checksum
        assertFails {
            scd40.getDataReadyStatus()
        }.also {
            assertThat(it.message).isEqualTo("CRC8 mismatch when reading 0-th word, calculated: 0xf0, read: 0x4, request: [228, 184], read data: [129, 6, 4]")
        }
    }
}