package eu.slomkowski.octoglow.octoglowd.hardware

import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.apache.commons.io.EndianUtils.readSwappedShort
import org.apache.commons.io.EndianUtils.readSwappedUnsignedShort
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.CoroutineContext

data class IndoorWeatherReport(
        val temperature: Double,
        val humidity: Double,
        val pressure: Double) {

    init {
        require(temperature in (-10f..60f))
        require(humidity in 0..100)
        require(pressure in 800..1200)
    }

    companion object {
        fun parse(compensationData: CompensationData, adcP: Int, adcT: Int, adcH: Int): IndoorWeatherReport {
            TODO()
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

        fun checkNot00andNotFF(list: ByteArray) {

            if (list.all { it.toInt() == 0 }) {
                throw IllegalStateException("data is all zeroes")
            }

            if (list.all { it.toInt() == 0xff }) {
                throw IllegalStateException("data is all 0xff")
            }
        }
    }

    override fun close() {}

    private val compensationData: CompensationData

    init {
        val (cal1, cal2) = runBlocking {
            doWrite(0xe0, 0xb6)

            delay(30)

            doWrite(0xf2, 0b101,
                    0xf5, 0b10110000,
                    0xf4, 0b10110111)

            delay(200)

            doTransaction(listOf(0x88), 25).toByteArray() to
                    doTransaction(listOf(0xe1), 8).toByteArray()
        }

        checkNot00andNotFF(cal1)
        checkNot00andNotFF(cal2)

        compensationData = CompensationData(
                t1 = readSwappedUnsignedShort(cal1, 0),
                t2 = readSwappedShort(cal1, 2).toInt(),
                t3 = readSwappedShort(cal1, 4).toInt(),

                p1 = readSwappedUnsignedShort(cal1, 6),
                p2 = readSwappedShort(cal1, 8).toInt(),
                p3 = readSwappedShort(cal1, 10).toInt(),
                p4 = readSwappedShort(cal1, 12).toInt(),
                p5 = readSwappedShort(cal1, 14).toInt(),
                p6 = readSwappedShort(cal1, 16).toInt(),
                p7 = readSwappedShort(cal1, 18).toInt(),
                p8 = readSwappedShort(cal1, 20).toInt(),
                p9 = readSwappedShort(cal1, 22).toInt(),

                h1 = java.lang.Byte.toUnsignedInt(cal1[24]),
                h2 = readSwappedShort(cal2, 0).toInt(),
                h3 = java.lang.Byte.toUnsignedInt(cal2[2]),
                h4 = (cal2[3].toInt() shl 4) + (cal2[4].toInt() and 0x0f),
                h5 = ((cal2[4].toInt() shr 4) and 0x0f) + (cal2[5].toInt() shl 4),
                h6 = cal2[7].toInt())
    }

    suspend fun readReport(): IndoorWeatherReport {
        val buf = doTransaction(listOf(0xf7), 8)

        if (buf == uninitializedSensorReport) {
            throw IllegalStateException("sensor is not initialized")
        }

        val bb = ByteBuffer.wrap(buf.toByteArray()).order(ByteOrder.BIG_ENDIAN)
        val adcP = bb.getInt(0) shr 12
        val adcT = bb.getInt(3) shr 12
        val adcH = bb.getShort(6).toInt()

        return IndoorWeatherReport.parse(compensationData, adcP, adcT, adcH)
    }
}