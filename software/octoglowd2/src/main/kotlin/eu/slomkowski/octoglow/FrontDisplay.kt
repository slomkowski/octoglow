package eu.slomkowski.octoglow

import io.dvlopt.linux.i2c.I2CBuffer
import io.dvlopt.linux.i2c.I2CBus
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

enum class Slot(val capacity: Int) {
    SLOT0(150),
    SLOT1(70),
    SLOT2(30)
}

class FrontDisplay(i2c: I2CBus) : I2CDevice(i2c, 0x14), HasBrightness {

    private val writeBuffer = I2CBuffer(500)
    private val readBuffer = I2CBuffer(8)

    override suspend fun setBrightness(brightness: Int) {
        selectSlave()
        i2c.write(writeBuffer.set(0, 7).set(1, brightness), 2)
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
        i2c.write(writeBuffer.set(0, 2), 1)
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
}