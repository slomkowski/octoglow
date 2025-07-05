package eu.slomkowski.octoglow.octoglowd.hardware

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class Scd40measurements(
    val co2: Double,
    val temperature: Double,
    val humidity: Double,
) {
    companion object {
        fun parse(result: IntArray): Scd40measurements {
            require(result.size == 3)
            val co2 = result[0].toDouble()
            val temperature = -45.0 + 175.0 * result[1].toDouble() / 65535.0
            val humidity = 100.0 * result[2].toDouble() / 65535.0

            return Scd40measurements(co2, temperature, humidity)
        }
    }

    init {
        require(
            temperature in (-10.0..60.0)
                    && humidity in (0.0..100.0)
                    && co2 in (350.0..2500.0)
        )
        { String.format("invalid report: %4.2f deg C, H: %3.0f%%, CO2: %4.0f ppm", temperature, humidity, co2) }
    }
}

class Scd40(hardware: Hardware) : I2cDevice(hardware, 0x62, logger) {

    companion object {
        private val logger = KotlinLogging.logger {}

        fun splitToBytes(command: Int): IntArray = intArrayOf((command shr 8) and 0xff, command and 0xff)

        fun calculateCrc8(value: Int) = calculateCrc8(splitToBytes(value))

        fun calculateCrc8(data: IntArray): Int {
            var crc = 0xff

            for (byte in data) {
                crc = 0xff and (crc xor byte)
                repeat(8) {
                    crc = if (crc and 0x80 != 0) {
                        (crc shl 1) xor 0x31
                    } else {
                        crc shl 1
                    } and 0xff
                }
            }

            return crc
        }
    }

    override suspend fun initDevice() {
        stopPeriodicMeasurement()

        val serialNumber = readSerialNumber()
        logger.info { "Serial number is $serialNumber." }
        startPeriodicMeasurement()
    }

    override suspend fun closeDevice() {
        stopPeriodicMeasurement()
    }

    suspend fun startPeriodicMeasurement() {
        devSendCommand(0x21b1)
    }

    suspend fun stopPeriodicMeasurement() {
        devSendCommand(0x3f86)
        delay(500)
    }

    suspend fun readMeasurementWithWaiting(): Scd40measurements = withTimeout(10.seconds) {
        while (!getDataReadyStatus()) {
            delay(200.milliseconds)
        }
        readMeasurement()
    }

    suspend fun getDataReadyStatus(): Boolean {
        val result = devRead(0xe4b8, 1)
        return (result[0] and 0b11111111111) != 0
    }

    suspend fun performSelfTest() {
        val result = devRead(0x3639, 1, 10.seconds)
        check(result[0] == 0) { "Self test failed with error: ${result[0]}" }
    }

    suspend fun readMeasurement(): Scd40measurements {
        val result = devRead(0xec05, 3)
        return Scd40measurements.parse(result)
    }

    suspend fun readSerialNumber(): Long {
        val result = devRead(0x3682, 3)
        return (result[0].toLong() shl 32) or (result[1].toLong() shl 16) or result[2].toLong()
    }

    private suspend fun devSendCommand(command: Int) {
        doWrite(*splitToBytes(command))
    }

    suspend fun setAmbientPressure(pressure: Double) {
        require(pressure in 800.0..1100.0) { "Pressure must be between 800 and 1100 hPa" }
        val p = pressure.roundToInt()
        logger.info { "Setting ambient pressure to $p hPa." }
        devWrite(0xe000, p)
    }

    private suspend fun devWrite(command: Int, value: Int) {
        doWrite(*splitToBytes(command), *splitToBytes(value), calculateCrc8(value))
    }

    private suspend fun devRead(command: Int, numberOfWords: Int, delay: Duration = 1.milliseconds): IntArray {
        require(numberOfWords in 1..3)
        val bufferLen = numberOfWords * 3
        val commandBytes = splitToBytes(command)
        val readBuffer = doTransaction(commandBytes, bufferLen, delay)
        check(readBuffer.size == bufferLen) { "invalid number of bytes read, expected $bufferLen, got ${readBuffer.size}" }

        return (0 until numberOfWords).map { idx ->
            val offset = 3 * idx
            val readValue = (readBuffer[offset + 0] shl 8) + (readBuffer[offset + 1])
            val calculatedCrc8 = calculateCrc8(readValue)
            val readCrc8 = readBuffer[offset + 2]
            check(calculatedCrc8 == readCrc8) {
                "CRC8 mismatch when reading $idx-th word, " +
                        "calculated: 0x${calculatedCrc8.toString(16)}, " +
                        "read: 0x${readCrc8.toString(16)}, " +
                        "request: ${commandBytes.contentToString()}, " +
                        "read data: ${readBuffer.contentToString()}"
            }

            readValue
        }.toIntArray()
    }
}