package eu.slomkowski.octoglow.octoglowd.hardware.mock

import eu.slomkowski.octoglow.octoglowd.hardware.ButtonReport
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.FrontDisplay
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.fail
import java.nio.charset.StandardCharsets


class FrontDisplayMock : FrontDisplay {

    private var brightness = 5

    private val upperBar = BooleanArray(20) { false }
    private val displayContent = CharArray(40) { ' ' }

    val upperBarContent: String
        get() = upperBar.joinToString("") {
            when (it) {
                true -> "*"
                else -> " "
            }
        }

    private var constructionYearByte: Byte = 66

    val line1content: String
        get() = displayContent.copyOfRange(0, 20).joinToString("")

    val line2content: String
        get() = displayContent.copyOfRange(20, 40).joinToString("")

    data class ScrollingTextHandle(
        val slot: Slot,
        val position: Int,
        val text: String,
    ) {
        init {
            assertThat(text.length).isGreaterThan(0)
            assertThat(text.length).isLessThanOrEqualTo(slot.capacity)
        }
    }

    val scrollingTextContent: Map<Slot, String>
        get() = scrollingTextBuffer.mapValues { it.value.text }

    private val scrollingTextBuffer = mutableMapOf<Slot, ScrollingTextHandle>()

    override suspend fun initDevice() {
    }

    override suspend fun setBrightness(brightness: Int) {
        this.brightness = brightness
    }

    override suspend fun getButtonReport(): ButtonReport {
        return ButtonReport(ButtonState.NO_CHANGE, 0)
    }

    override suspend fun clear() {
        scrollingTextBuffer.clear()
        upperBar.fill(false)
        displayContent.fill(' ')
    }

    override suspend fun setText(textBytes: ByteArray, header: IntArray) {
        when (header[0]) {
            4 -> setStatic(textBytes, header)
            5 -> setScrolling(textBytes, header)
        }
    }

    private fun setScrolling(textBytes: ByteArray, header: IntArray) {
        assertThat(header.size).isEqualTo(4)
        assertThat(header[0]).isEqualTo(5)
        val slot = Slot.entries[header[1]]
        val position = header[2]
        val windowLength = header[3]

        val text = textBytes.toString(StandardCharsets.UTF_8)

        require(position in 0..39) { "Invalid position: $position" }
        require(position + windowLength <= 40) { "Text exceeds display boundaries." }
        for (index in 0..<windowLength) {
            displayContent[position + index] = '#'
        }

        scrollingTextBuffer[slot] = ScrollingTextHandle(
            slot, position, text
        )
    }

    private fun setStatic(textBytes: ByteArray, header: IntArray) {
        assertThat(header.size).isEqualTo(3)
        assertThat(header[0]).isEqualTo(4) // support only static text
        val position = header[1]
        val text = textBytes.toString(StandardCharsets.UTF_8)
        assertThat(text.length).isEqualTo(header[2])

        require(position in 0..39) { "Invalid position: $position" }
        require(text.length + position <= 40) { "Text exceeds display boundaries." }
        text.forEachIndexed { index, char ->
            displayContent[position + index] = char
        }
    }

    override suspend fun getEndOfConstructionYearInternal(): Byte {
        return constructionYearByte
    }

    override suspend fun setEndOfConstructionYearInternal(lastDigitsOfYear: Byte) {
        constructionYearByte = lastDigitsOfYear
    }

    override suspend fun <T : Number> setOneLineDiffChart(position: Int, currentValue: T?, historicalValues: List<T?>?, unit: T) {
        fail("Graphical functions are not implemented for mock front display")
    }

    override suspend fun <T : Number> setTwoLinesDiffChart(position: Int, currentValue: T, historicalValues: List<T?>, unit: T) {
        fail("Graphical functions are not implemented for mock front display")
    }

    override suspend fun setUpperBar(c: Int) {
        (0..19).forEach { idx ->
            upperBar[idx] = (1 shl idx) and c > 0
        }
    }

    override suspend fun closeDevice() {
    }

    fun assertDisplayContent(
        upperBar: String,
        line1: String,
        line2: String
    ) {
        assertThat(upperBar).hasSize(20)
        assertThat(line1).hasSize(20)
        assertThat(line2).hasSize(20)

        println(renderDisplayContent())
        println()

        assertThat(line1content).isEqualTo(line1)
        assertThat(line2content).isEqualTo(line2)
        assertThat(upperBarContent).isEqualTo(upperBar)
    }

    fun renderDisplayContent(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("<", upperBarContent, ">\n")
        stringBuilder.append("|", line1content, "|\n")
        stringBuilder.append("|", line2content, "|")

        if (scrollingTextBuffer.isNotEmpty()) {
            stringBuilder.append("\nScrolling text:\n")
        }

        scrollingTextBuffer.values.map { "* slot ${it.slot}: ${it.text}" }.sorted().forEach {
            stringBuilder.appendLine(it)
        }

        return stringBuilder.toString().trim()
    }
}