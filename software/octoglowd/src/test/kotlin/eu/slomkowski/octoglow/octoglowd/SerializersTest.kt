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
    fun `test serialization with two decimal places`() {
        fun assertValid(result: Double, orig: Double) {
            val encoderMock = mockk<Encoder>()
            every { encoderMock.encodeDouble(any()) } returns Unit
            RoundedDoubleSerializer.serialize(encoderMock, orig)
            val strSlot = slot<Double>()
            verify(exactly = 1) { encoderMock.encodeDouble(capture(strSlot)) }
            assertThat(strSlot.captured).isEqualTo(result)
        }

        assertValid(12.37, 12.3669420022)
    }
}