package eu.slomkowski.octoglow.hardware

import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
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
        val encoderDelta: Int) {
    init {
        require(encoderDelta in -127..127) { "valid delta is between -127 and 127" }
    }
}

class FrontDisplay(i2c: I2CBus) : I2CDevice(i2c, 0x14), HasBrightness {

    private val writeBuffer = I2CBuffer(500)

    // we assume last value as pivot
    private val maxValuesInChart = 5 * 20

    override suspend fun setBrightness(brightness: Int) {
        selectSlave()
        i2c.smbus.writeWord(3, brightness)
    }

    /**
     * Sets the upper bar content - all 20 positions are needed.
     */
    suspend fun setUpperBar(content: Array<Boolean>) {
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
        selectSlave()
        val bk = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(c)
        i2c.write(writeBuffer.set(0, 7).set(1, bk[0]).set(2, bk[1]).set(3, bk[2]).set(4, bk[3]), 5)
    }

    suspend fun clear() {
        selectSlave()
        i2c.smbus.writeByteDirectly(2)
    }

    suspend fun setStaticText(position: Int, text: String) {
        val lastPosition = position + text.length - 1
        require(position < 40) { "position has to be between 0 and 39, $position provided" }
        require(text.isNotEmpty()) { "text length has to be at least 1" }
        require(lastPosition < 40) {
            "end of the string cannot exceed position 39, but has length ${text.length} " +
                    "and position $position, which sums to ${text.length + position}"
        }

        writeBuffer.set(0, 4).set(1, position).set(2, text.length)
        setText(text.toByteArray(StandardCharsets.UTF_8), 3)
    }

    suspend fun setScrollingText(slot: Slot, position: Int, length: Int, text: String) {
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val lastPosition = position + length - 1
        require(slot.capacity > textBytes.size) { "UTF-8 text length ({} bytes) cannot exceed the capacity of the selected slot $slot, which is ${slot.capacity}" }
        require(position < 40) { "position has to be between 0 and 39, $position provided" }
        require(text.isNotEmpty()) { "text length has to be at least 1" }
        require(lastPosition < 40) { "end of the string cannot exceed position 39, but has length ${textBytes.size} and position $position, which sums to $lastPosition" }

        writeBuffer.set(0, 5).set(1, slot.ordinal).set(2, position).set(3, length)
        setText(textBytes, 4)
    }

    private suspend fun setText(textBytes: ByteArray, offset: Int) {
        textBytes.forEachIndexed { idx, b -> writeBuffer.set(idx + offset, b) }
        writeBuffer.set(offset + textBytes.size, 0)

        selectSlave()
        i2c.write(writeBuffer, offset + textBytes.size + 1)
    }

    suspend fun <T : Number> setOneLineDiffChart(position: Int, values: List<T>, unit: T) {
        require(values.size < maxValuesInChart) { "number of values cannot exceed $maxValuesInChart" }
        require(values.isNotEmpty()) { "there has to be at least one value" }
        val maxPosition = 5 * 40 - values.size + 1
        require(position < maxPosition) { "position cannot exceed $maxPosition" }

        val last = values.last().toDouble()
        val img = values
                .map { ((it.toDouble() - last) / unit.toDouble()).roundToInt().coerceIn(-3, 3) }
                .map {
                    when (it) {
                        -3 -> 0b1111000
                        -2 -> 0b0111000
                        -1 -> 0b0011000
                        0 -> 0b0001000
                        1 -> 0b1100
                        2 -> 0b1110
                        3 -> 0b1111
                        else -> throw IllegalStateException()
                    }
                }

        selectSlave()
        drawImage(position, false, img)
    }

    suspend fun <T : Number> setTwoLinesDiffChart(position: Int, values: List<T>, unit: T) {
        require(values.size < maxValuesInChart) { "number of values cannot exceed $maxValuesInChart" }
        require(values.isNotEmpty()) { "there has to be at least one value" }
        val maxPosition = 5 * 20 - values.size
        require(position < maxPosition) { "position cannot exceed $maxPosition" }

        val current = values.last().toDouble()
        val historical = values.dropLast(1)

        val (upper, lower) = historical
                .map {
                    val v = ((it.toDouble() - current) / unit.toDouble()).roundToInt()
                    val (upperV, lowerV) = v.coerceIn(0, 7) to v.coerceIn(-7, 0)

                    val upperC = (0..(upperV - 1)).fold(0) { columnByte, y -> columnByte or (0b1000000 shr y) }
                    val lowerC = (0..(-lowerV - 1)).fold(0) { columnByte, y -> columnByte or (1 shl y) }

                    upperC to lowerC
                }
                .plus(0b1000000 to 0b1)
                .unzip()

        selectSlave()
        drawImage(position, false, upper)
        drawImage(5 * 20 + position, false, lower)
    }

    private suspend fun drawImage(position: Int, sumWithExistingText: Boolean, content: List<Int>) {
        writeBuffer
                .set(0, 6)
                .set(1, position)
                .set(2, content.size)
                .set(3, if (sumWithExistingText) {
                    1
                } else {
                    0
                })

        content.forEachIndexed { idx, v -> writeBuffer.set(idx + 4, v) }

        i2c.write(writeBuffer, content.size + 4)
    }

    suspend fun getButtonReport(): ButtonReport {
        val readBuffer = I2CBuffer(2)
        doTransaction(I2CBuffer(1).set(0, 1), readBuffer)

        return ButtonReport(when (readBuffer[1]) {
            255 -> ButtonState.JUST_RELEASED
            1 -> ButtonState.JUST_PRESSED
            0 -> ButtonState.NO_CHANGE
            else -> throw IllegalStateException("invalid button state value: ${readBuffer[1]}")
        }, if (readBuffer[0] <= 127) {
            readBuffer[0]
        } else {
            readBuffer[0] - 255
        })
    }
}