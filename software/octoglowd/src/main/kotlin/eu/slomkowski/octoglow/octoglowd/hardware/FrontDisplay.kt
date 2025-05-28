package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.set
import io.dvlopt.linux.i2c.I2CBuffer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

enum class Slot(val capacity: Int) {
    SLOT0(150),
    SLOT1(70),
    SLOT2(30)
}

enum class ButtonState {
    NO_CHANGE,
    JUST_PRESSED,
    JUST_RELEASED
}

data class ButtonReport(
    val button: ButtonState,
    val encoderDelta: Int
) {
    init {
        require(encoderDelta in -127..127) { "valid delta is between -127 and 127" }
    }
}

class FrontDisplay(hardware: Hardware) : I2CDevice(hardware, 0x14), HasBrightness {

    companion object {
        private val logger = KotlinLogging.logger {}

        // we assume last value as pivot
        private const val MAX_VALUES_IN_CHART = 5 * 20
    }

    init {
        runBlocking {
            clear()
            setBrightness(3)
        }
    }

    override suspend fun closeDevice() {
        clear()
        setBrightness(3)
        setStaticText(5, "SHUT  DOWN")
    }

    override suspend fun setBrightness(brightness: Int) {
        doWrite(3, brightness)
    }

    /**
     * Sets the upper bar content - all 20 positions are needed.
     */
    suspend fun setUpperBar(content: BooleanArray) {
        require(content.size == 20) { "array size has to be 20" }

        val c = content.foldIndexed(0) { index, acc, value ->
            when (value) {
                true -> (acc or (1 shl index))
                false -> acc
            }
        }
        setUpperBar(c)
    }

    suspend fun setUpperBar(activePositions: Collection<Int>, invert: Boolean = false) {
        require(activePositions.all { it <= 19 }) { "position numbers between 0 and 19 are allowed" }

        val c = activePositions
            .fold(0) { acc, pos -> acc or (1 shl pos) }
            .let {
                when (invert) {
                    true -> it.inv()
                    else -> it
                }
            }
        setUpperBar(c)
    }

    private suspend fun setUpperBar(c: Int) {
        val bk = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(c)
        doWrite(I2CBuffer(5).set(0, 7).set(1, bk[0]).set(2, bk[1]).set(3, bk[2]).set(4, bk[3]))
    }

    suspend fun clear() {
        doWrite(2)
    }

    suspend fun setStaticText(position: Int, text: String) {
        val lastPosition = position + text.length - 1
        require(position < 40) { "position has to be between 0 and 39, $position provided" }
        require(text.isNotEmpty()) { "text length has to be at least 1" }
        require(lastPosition < 40) {
            "end of the string cannot exceed position 39, but has length ${text.length} " +
                    "and position $position, which sums to ${text.length + position}, text: $text"
        }

        setText(text.toByteArray(StandardCharsets.UTF_8), intArrayOf(4, position, text.length))
    }

    suspend fun setScrollingText(slot: Slot, position: Int, length: Int, text: String) {
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val lastPosition = position + length - 1
        require(slot.capacity >= textBytes.size) { "UTF-8 text length (${textBytes.size} bytes) cannot exceed the capacity of the selected slot $slot, which is ${slot.capacity}" }
        require(position < 40) { "position has to be between 0 and 39, $position provided" }
        require(text.isNotEmpty()) { "text length has to be at least 1" }
        require(lastPosition < 40) { "end of the string cannot exceed position 39, but has length ${textBytes.size} and position $position, which sums to $lastPosition, text: $text" }

        setText(textBytes, intArrayOf(5, slot.ordinal, position, length))
    }

    private suspend fun setText(textBytes: ByteArray, header: IntArray) {
        val writeBuffer = I2CBuffer(header.size + textBytes.size + 1)
        header.forEachIndexed { idx, v -> writeBuffer.set(idx, v) }
        textBytes.forEachIndexed { idx, b -> writeBuffer.set(idx + header.size, b) }
        writeBuffer.set(header.size + textBytes.size, 0)

        doWrite(writeBuffer)
    }

    suspend fun <T : Number> setOneLineDiffChart(position: Int, currentValue: T, historicalValues: List<T?>, unit: T) {
        require(historicalValues.size + 1 < MAX_VALUES_IN_CHART) { "number of values cannot exceed $MAX_VALUES_IN_CHART" }
        require(historicalValues.isNotEmpty()) { "there has to be at least one value" }
        val maxPosition = 5 * 40 - historicalValues.size + 2
        require(position < maxPosition) { "position cannot exceed $maxPosition" }

        val img = historicalValues
            .map { nullableHistVal ->
                val diff = nullableHistVal?.let {
                    ((it.toDouble() - currentValue.toDouble()) / unit.toDouble()).roundToInt().coerceIn(-3, 3)
                }
                when (diff) {
                    -3 -> 0b1111000
                    -2 -> 0b0111000
                    -1 -> 0b0011000
                    0 -> 0b0001000
                    1 -> 0b1100
                    2 -> 0b1110
                    3 -> 0b1111
                    null -> 0b1000001
                    else -> throw IllegalStateException()
                }
            }
            .plus(0b0001000)

        drawImage(position, false, img)
    }

    suspend fun <T : Number> setTwoLinesDiffChart(position: Int, currentValue: T, historicalValues: List<T?>, unit: T) {
        require(historicalValues.size < MAX_VALUES_IN_CHART) { "number of values cannot exceed $MAX_VALUES_IN_CHART" }
        require(historicalValues.isNotEmpty()) { "there has to be at least one value" }
        val maxPosition = 5 * 20 - historicalValues.size
        require(position < maxPosition) { "position cannot exceed $maxPosition" }

        val (upper, lower) = historicalValues
            .map { nullableHistVal ->
                nullableHistVal?.let {
                    val v = ((it.toDouble() - currentValue.toDouble()) / unit.toDouble()).roundToInt()
                    val (upperV, lowerV) = v.coerceIn(0, 7) to v.coerceIn(-7, 0)

                    val upperC = (0 until upperV).fold(0) { columnByte, y -> columnByte or (0b1000000 shr y) }
                    val lowerC = (0 until -lowerV).fold(0) { columnByte, y -> columnByte or (1 shl y) }

                    upperC to lowerC
                } ?: 0b1 to 0b1000000
            }
            .plus(0b1000000 to 0b1)
            .unzip()

        drawImage(position, false, upper)
        drawImage(5 * 20 + position, false, lower)
    }

    private suspend fun drawImage(position: Int, sumWithExistingText: Boolean, content: List<Int>) {
        val writeBuffer = I2CBuffer(content.size + 4)
            .set(0, 6)
            .set(1, position)
            .set(2, content.size)
            .set(
                3, if (sumWithExistingText) {
                    1
                } else {
                    0
                }
            )

        content.forEachIndexed { idx, v -> writeBuffer.set(idx + 4, v) }

        doWrite(writeBuffer)
    }

    suspend fun getButtonReport(): ButtonReport {
        val readBuffer = doTransaction(I2CBuffer(1).set(0, 1), 2)

        return ButtonReport(
            when (readBuffer[1]) {
                255 -> ButtonState.JUST_RELEASED
                1 -> ButtonState.JUST_PRESSED
                0 -> ButtonState.NO_CHANGE
                else -> throw IllegalStateException("invalid button state value: ${readBuffer[1]}")
            }, if (readBuffer[0] <= 127) {
                readBuffer[0]
            } else {
                readBuffer[0] - 256
            }
        )
    }
}