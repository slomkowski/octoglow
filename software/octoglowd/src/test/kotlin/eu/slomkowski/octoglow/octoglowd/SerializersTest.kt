@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SerializersTest {

    @Test
    fun testUriDeserializer() {
        fun assertValid(str: String, uri: URI) {
            val decoderMock = mockk<Decoder>()
            every { decoderMock.decodeString() } returns str
            assertThat(UriSerializer.deserialize(decoderMock)).isEqualTo(uri)
        }

        assertValid("https://example.org", URI("https://example.org"))
        assertValid("https://example.org/hello/world.txt", URI("https://example.org/hello/world.txt"))
    }

    @Test
    fun testUriSerializer() {
        fun assertValid(str: String, uri: URI) {
            val encoderMock = mockk<Encoder>()
            every { encoderMock.encodeString(any()) } returns Unit
            UriSerializer.serialize(encoderMock, uri)
            val strSlot = slot<String>()
            verify(exactly = 1) { encoderMock.encodeString(capture(strSlot)) }
            assertThat(strSlot.captured).isEqualTo(str)
        }

        assertValid("https://example.org", URI("https://example.org"))
        assertValid("https://example.org/hello/world.txt", URI("https://example.org/hello/world.txt"))
    }

    @Test
    fun testSimpleMonitorInstantSerializer() {
        fun assertValid(str: String, expected: Instant) {
            val decoderMock = mockk<Decoder>()
            every { decoderMock.decodeString() } returns str
            assertThat(SimpleMonitorInstantSerializer.deserialize(decoderMock)).isEqualTo(expected)
        }

        // positive whole-hour offset
        assertValid("2024-01-01 12:00:00+02:00", Instant.parse("2024-01-01T10:00:00Z"))
        // UTC
        assertValid("2024-01-01 12:00:00+00:00", Instant.parse("2024-01-01T12:00:00Z"))
        // positive offset with non-zero minutes
        assertValid("2024-01-01 12:00:00+05:30", Instant.parse("2024-01-01T06:30:00Z"))
        // negative whole-hour offset
        assertValid("2024-01-01 12:00:00-05:00", Instant.parse("2024-01-01T17:00:00Z"))
        // negative offset with non-zero minutes — previously threw due to sign mismatch
        assertValid("2024-01-01 12:00:00-05:30", Instant.parse("2024-01-01T17:30:00Z"))
        assertValid("2024-06-15 08:15:30-03:30", Instant.parse("2024-06-15T11:45:30Z"))
        // negative sub-hour offset, hour component is zero
        assertValid("2024-01-01 12:00:00-00:30", Instant.parse("2024-01-01T12:30:00Z"))
    }
}