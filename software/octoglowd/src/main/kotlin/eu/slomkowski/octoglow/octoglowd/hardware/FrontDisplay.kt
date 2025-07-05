package eu.slomkowski.octoglow.octoglowd.hardware

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

enum class Slot(val capacity: Int) {
    SLOT0(148), // should be 150, but probably firmware bug
    SLOT1(68),
    SLOT2(28),
}

enum class ButtonState {
    NO_CHANGE,
    JUST_PRESSED,
    JUST_RELEASED,
}

data class ButtonReport(
    val button: ButtonState,
    val encoderDelta: Int
) {
    init {
        require(encoderDelta in -127..127) { "valid delta is between -127 and 127" }
    }
}

interface FrontDisplay {
    suspend fun initDevice()

    suspend fun closeDevice()

    suspend fun setBrightness(brightness: Int)

    suspend fun getButtonReport(): ButtonReport

    suspend fun clear()

    suspend fun <T : Number> setOneLineDiffChart(position: Int, currentValue: T, historicalValues: List<T?>, unit: T)

    suspend fun <T : Number> setTwoLinesDiffChart(position: Int, currentValue: T, historicalValues: List<T?>, unit: T)

    suspend fun setEndOfConstructionYear(year: Int) {
        require(year in 2016..2099) { "year is in 2016..2099" }
        setEndOfConstructionYearInternal((year - 2000).toByte())
    }

    suspend fun getEndOfConstructionYear(): Int = 2000 + getEndOfConstructionYearInternal()

    suspend fun getEndOfConstructionYearInternal(): Byte

    suspend fun setEndOfConstructionYearInternal(lastDigitsOfYear: Byte)

    suspend fun setUpperBar(c: Int)

    suspend fun setText(textBytes: ByteArray, header: IntArray)

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
}

class FrontDisplayReal(hardware: Hardware) : CustomI2cDevice(hardware, logger, 0x14), HasBrightness, FrontDisplay {

    companion object {
        private val logger = KotlinLogging.logger {}

        // we assume last value as pivot
        private const val MAX_VALUES_IN_CHART = 5 * 20

        private val getButtonReportCmd = intArrayOf(1)
    }

    override suspend fun initDevice() {
        clear()
        setBrightness(3)

        val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        setEndOfConstructionYear(currentYear)
    }

    override suspend fun closeDevice() {
        clear()
        setBrightness(3)
        setStaticText(5, "SHUT  DOWN")
    }

    override suspend fun setBrightness(brightness: Int) {
        sendCommand("set brightness", 3, brightness)
    }

    override suspend fun setUpperBar(c: Int) {
        val bk = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(c)
        sendCommand("set upper bar content", 7, bk[0].toInt(), bk[1].toInt(), bk[2].toInt())
    }

    override suspend fun clear() {
        sendCommand("clear display", 2)
    }

    override suspend fun setText(textBytes: ByteArray, header: IntArray) {
        val writeBuffer = IntArray(header.size + textBytes.size + 1)
        header.forEachIndexed { idx, v -> writeBuffer[idx] = v }
        textBytes.forEachIndexed { idx, b -> writeBuffer[idx + header.size] = b.toInt() }
        writeBuffer[header.size + textBytes.size] = 0
        sendCommand("set text", *writeBuffer)
    }

    override suspend fun getEndOfConstructionYearInternal(): Byte {
        val readBuffer = sendCommandAndReadData("get end-of-construction year", 3, 8)
        return readBuffer[2].toByte()
    }

    override suspend fun setEndOfConstructionYearInternal(lastDigitsOfYear: Byte) {
        sendCommand("set end-of-construction year", 9, lastDigitsOfYear.toInt())
    }

    override suspend fun <T : Number> setOneLineDiffChart(position: Int, currentValue: T, historicalValues: List<T?>, unit: T) {
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
                    else -> error("Unexpected state encountered")
                }
            }
            .plus(0b0001000)

        drawImage(position, false, img)
    }

    override suspend fun <T : Number> setTwoLinesDiffChart(position: Int, currentValue: T, historicalValues: List<T?>, unit: T): Unit = coroutineScope {
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

        launch { drawImage(position, false, upper) }
        launch { drawImage(5 * 20 + position, false, lower) }
    }

    private suspend fun drawImage(position: Int, sumWithExistingText: Boolean, content: List<Int>) {
        val writeBuffer = IntArray(content.size + 4).apply {
            set(0, 6)
            set(1, position)
            set(2, content.size)
            set(
                3, if (sumWithExistingText) {
                    1
                } else {
                    0
                }
            )
        }

        content.forEachIndexed { idx, v -> writeBuffer[idx + 4] = v }

        sendCommand("draw image", *writeBuffer)
    }

    override suspend fun getButtonReport(): ButtonReport {
        val readBuffer = sendCommandAndReadData("get button report", 4, 1)

        return ButtonReport(
            when (readBuffer[3]) {
                255 -> ButtonState.JUST_RELEASED
                1 -> ButtonState.JUST_PRESSED
                0 -> ButtonState.NO_CHANGE
                else -> error("invalid button state value: ${readBuffer[3]}")
            }, if (readBuffer[2] <= 127) {
                readBuffer[2]
            } else {
                readBuffer[2] - 256
            }
        )
    }
}