package eu.slomkowski.octoglow.octoglowd.hardware

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class Scd40Test {
    @Test
    fun `splitToBytes should correctly split a 16-bit command into two bytes`() {
        val command = 0x21B1
        val expected = intArrayOf(0x21, 0xB1)
        val result = Scd40.Companion.splitToBytes(command)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `splitToBytes should correctly split a command with all bytes set to zero`() {
        val command = 0x0000
        val expected = intArrayOf(0x00, 0x00)
        val result = Scd40.Companion.splitToBytes(command)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `splitToBytes should correctly split a command with maximum possible value`() {
        val command = 0xFFFF
        val expected = intArrayOf(0xFF, 0xFF)
        val result = Scd40.Companion.splitToBytes(command)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `splitToBytes should correctly split a command with low byte set to zero`() {
        val command = 0xFF00
        val expected = intArrayOf(0xFF, 0x00)
        val result = Scd40.Companion.splitToBytes(command)
        assertArrayEquals(expected, result)
    }

    @Test
    fun `splitToBytes should correctly split a command with high byte set to zero`() {
        val command = 0x00FF
        val expected = intArrayOf(0x00, 0xFF)
        val result = Scd40.Companion.splitToBytes(command)
        assertArrayEquals(expected, result)
    }
}