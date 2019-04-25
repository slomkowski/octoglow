package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.toList
import eu.slomkowski.octoglow.octoglowd.trySeveralTimes
import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import kotlin.coroutines.CoroutineContext
import kotlin.math.exp

/**
 * Temperature in deg C, humidity in %, pressure in hPa.
 */
data class IndoorWeatherReport(
        val temperature: Double,
        val humidity: Double,
        val realPressure: Double) {

    init {
        require(temperature in (-10f..60f) && humidity in (0.0..100.0) && realPressure in (900.0..1100.0))
        { String.format("invalid report: %4.2f deg C, H: %3.0f%%, %4.0f hPa", temperature, humidity, realPressure) }
    }

    /**
     * Calculate mean sea-level pressure using elevation in meters.
     */
    fun getMeanSeaLevelPressure(elevation: Double): Double {
        require(elevation in 0.0..1000.0)

        val u = 0.0289644
        val g = 9.81
        val r = 8.3144598
        val tempK = temperature + 273.15

        val coeff = exp(-u * g * elevation / (r * tempK))

        return realPressure / coeff
    }

    companion object {
        /*
        This code was directly transplanted from
        https://github.com/ControlEverythingCommunity/BME280/blob/master/Java/BME280.java
         */
        fun parse(cd: CompensationData, adcP: Int, adcT: Int, adcH: Int): IndoorWeatherReport {
            var var1 = (adcT.toDouble() / 16384.0 - cd.t1.toDouble() / 1024.0) * cd.t2.toDouble()
            var var2 = (adcT.toDouble() / 131072.0 - cd.t1.toDouble() / 8192.0) * (adcT.toDouble() / 131072.0 - cd.t1.toDouble() / 8192.0) * cd.t3.toDouble()
            val tFine = (var1 + var2).toLong().toDouble()
            val cTemp = (var1 + var2) / 5120.0

            var1 = tFine / 2.0 - 64000.0
            var2 = var1 * var1 * cd.p6.toDouble() / 32768.0
            var2 += var1 * cd.p5.toDouble() * 2.0
            var2 = var2 / 4.0 + cd.p4.toDouble() * 65536.0
            var1 = (cd.p3.toDouble() * var1 * var1 / 524288.0 + cd.p2.toDouble() * var1) / 524288.0
            var1 = (1.0 + var1 / 32768.0) * cd.p1.toDouble()
            var p = 1048576.0 - adcP.toDouble()
            p = (p - var2 / 4096.0) * 6250.0 / var1
            var1 = cd.p9.toDouble() * p * p / 2147483648.0
            var2 = p * cd.p8.toDouble() / 32768.0
            val pressure = (p + (var1 + var2 + cd.p7.toDouble()) / 16.0) / 100

            // Humidity offset calculations
            var varH = tFine - 76800.0
            varH = (adcH - (cd.h4 * 64.0 + cd.h5 / 16384.0 * varH)) * (cd.h2 / 65536.0 * (1.0 + cd.h6 / 67108864.0 * varH * (1.0 + cd.h3 / 67108864.0 * varH)))
            val humidity = varH * (1.0 - cd.h1 * varH / 524288.0)

            return IndoorWeatherReport(cTemp, humidity, pressure)
        }
    }
}

data class CompensationData(
        val t1: Int,
        val t2: Int,
        val t3: Int,

        val p1: Int,
        val p2: Int,
        val p3: Int,
        val p4: Int,
        val p5: Int,
        val p6: Int,
        val p7: Int,
        val p8: Int,
        val p9: Int,

        val h1: Int,
        val h2: Int,
        val h3: Int,
        val h4: Int,
        val h5: Int,
        val h6: Int)

class Bme280(ctx: CoroutineContext, i2c: I2CBus) : I2CDevice(ctx, i2c, 0x76) {

    companion object : KLogging() {
        private val uninitializedSensorReport = listOf(128, 0, 0, 128, 0, 0, 128, 0)

        fun checkNot00andNotFF(buffer: I2CBuffer) {
            val list = buffer.toList()

            if (list.all { it == 0 }) {
                throw IllegalStateException("data is all zeroes")
            }

            if (list.all { it == 0xff }) {
                throw IllegalStateException("data is all 0xff")
            }
        }

        fun toSignedShort(ba: I2CBuffer, offset: Int): Int = toUnsignedShort(ba, offset).let { orig ->
            if (orig > 32767) {
                orig - 65536
            } else {
                orig
            }
        }

        fun toUnsignedShort(ba: I2CBuffer, offset: Int): Int {
            val bYounger = ba[offset].apply { check(this in 0..255) { "young byte: $this" } }
            val bOlder = ba[offset + 1].apply { check(this in 0..255) { "old byte: $this" } }
            return bYounger + 256 * bOlder
        }
    }

    override fun close() {
        // doesn't need any closing action
    }

    private val compensationData: CompensationData

    init {
        val (b1, b2) = runBlocking {
            doWrite(0xe0, 0xb6)

            delay(100)

            doWrite(0xf2, 0b100,
                    0xf5, 0b100_100_0_0,
                    0xf4, 0b100_100_11)

            delay(100)

            val id = doTransaction(listOf(0xd0), 1).get(0)
            check(id == 0x60) { String.format("this is not BME280 chip, it has ID 0x%x", id) }

            doTransaction(listOf(0x88), 25) to
                    doTransaction(listOf(0xe1), 8)
        }

        checkNot00andNotFF(b1)
        checkNot00andNotFF(b2)

        compensationData = CompensationData(
                t1 = toUnsignedShort(b1, 0),
                t2 = toSignedShort(b1, 2),
                t3 = toSignedShort(b1, 4),

                p1 = toUnsignedShort(b1, 6),
                p2 = toSignedShort(b1, 8),
                p3 = toSignedShort(b1, 10),
                p4 = toSignedShort(b1, 12),
                p5 = toSignedShort(b1, 14),
                p6 = toSignedShort(b1, 16),
                p7 = toSignedShort(b1, 18),
                p8 = toSignedShort(b1, 20),
                p9 = toSignedShort(b1, 22),

                h1 = b1[24],
                h2 = toSignedShort(b2, 0),
                h3 = b2[2],
                h4 = (b2[3] shl 4) + (b2[4] and 0x0f),
                h5 = ((b2[4] shr 4) and 0x0f) + (b2[5] shl 4),
                h6 = b2[7])

        logger.debug { "Compensation data: $compensationData" }
    }

    suspend fun readReport(): IndoorWeatherReport = trySeveralTimes(3, logger) {
        val buf = doTransaction(listOf(0xf7), 8)

        check(buf != uninitializedSensorReport) { "sensor is not initialized" }

        val adcP = buf[0] shl 12 or (buf[1] shl 4) or (buf[2] shr 4)
        val adcT = buf[3] shl 12 or (buf[4] shl 4) or (buf[5] shr 4)
        val adcH = buf[6] shl 8 or buf[7]

        logger.trace { "adcP: $adcP, adcT: $adcT, adcH: $adcH." }

        IndoorWeatherReport.parse(compensationData, adcP, adcT, adcH)
    }
}