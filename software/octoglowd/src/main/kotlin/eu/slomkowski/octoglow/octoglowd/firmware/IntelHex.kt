package eu.slomkowski.octoglow.octoglowd.firmware

import java.nio.file.Path
import kotlin.io.path.readLines

/**
 * Firmware image read from an Intel HEX file. Bytes not present in the file are 0xff (erased flash).
 */
class FirmwareImage(private val data: Map<Int, Int>) {
    init {
        require(data.isNotEmpty()) { "firmware image is empty" }
    }

    /**
     * Address after the last byte of the image.
     */
    val endAddress: Int = data.keys.max() + 1

    operator fun get(address: Int): Int = data[address] ?: 0xff

    fun slice(address: Int, length: Int) = IntArray(length) { get(address + it) }

    /**
     * The image with the addresses relative to [start], e.g. the start of the flash of MSP430.
     */
    fun relativeTo(start: Int): FirmwareImage {
        if (start == 0) {
            return this
        }
        val firstAddress = data.keys.min()
        require(firstAddress >= start) { "image starts at 0x${firstAddress.toString(16)}, below the flash start 0x${start.toString(16)}" }
        return FirmwareImage(data.mapKeys { it.key - start })
    }
}

object IntelHex {
    private const val RECORD_DATA = 0x00
    private const val RECORD_END_OF_FILE = 0x01
    private const val RECORD_EXTENDED_SEGMENT_ADDRESS = 0x02
    private const val RECORD_START_SEGMENT_ADDRESS = 0x03
    private const val RECORD_EXTENDED_LINEAR_ADDRESS = 0x04
    private const val RECORD_START_LINEAR_ADDRESS = 0x05

    fun parse(path: Path): FirmwareImage = parse(path.readLines())

    fun parse(lines: List<String>): FirmwareImage {
        val data = mutableMapOf<Int, Int>()
        var baseAddress = 0

        for ((lineIndex, rawLine) in lines.withIndex()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                continue
            }

            fun fail(message: String): Nothing = throw IllegalArgumentException("line ${lineIndex + 1}: $message")

            if (!line.startsWith(':') || line.length < 11 || line.length % 2 == 0) {
                fail("invalid record '$line'")
            }

            val bytes = try {
                line.substring(1).chunked(2).map { it.toInt(16) }
            } catch (_: NumberFormatException) {
                fail("invalid hex digits in '$line'")
            }

            val length = bytes[0]
            if (bytes.size != length + 5) {
                fail("record length $length doesn't match line length")
            }
            if (bytes.sum() and 0xff != 0) {
                fail("checksum error")
            }

            val offset = (bytes[1] shl 8) or bytes[2]
            val payload = bytes.subList(4, 4 + length)

            when (bytes[3]) {
                RECORD_DATA -> payload.forEachIndexed { i, b ->
                    val address = baseAddress + offset + i
                    if (data.put(address, b) != null) {
                        fail("address 0x${address.toString(16)} defined twice")
                    }
                }

                RECORD_END_OF_FILE -> return FirmwareImage(data)
                RECORD_EXTENDED_SEGMENT_ADDRESS -> baseAddress = ((payload[0] shl 8) or payload[1]) shl 4
                RECORD_EXTENDED_LINEAR_ADDRESS -> baseAddress = ((payload[0] shl 8) or payload[1]) shl 16
                RECORD_START_SEGMENT_ADDRESS, RECORD_START_LINEAR_ADDRESS -> Unit
                else -> fail("unsupported record type ${bytes[3]}")
            }
        }

        throw IllegalArgumentException("missing end of file record")
    }
}
