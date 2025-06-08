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
import java.util.*

class SerializersTest {

    @Test
    fun testLocaleDeserializer() {
        fun assertValid(str: String, locale: Locale) {
            val decoderMock = mockk<Decoder>()
            every { decoderMock.decodeString() } returns str
            assertThat(LocaleSerializer.deserialize(decoderMock)).isEqualTo(locale)
        }

        assertValid("en", Locale.ENGLISH)
        assertValid("en_US", Locale.US)
        assertValid("pl_PL", Locale("pl", "PL"))
    }

    @Test
    fun testLocaleSerializer() {
        fun assertValid(str: String, locale: Locale) {
            val encoderMock = mockk<Encoder>()
            every { encoderMock.encodeString(any()) } returns Unit
            LocaleSerializer.serialize(encoderMock, locale)
            val strSlot = slot<String>()
            verify(exactly = 1) { encoderMock.encodeString(capture(strSlot)) }
            assertThat(strSlot.captured).isEqualTo(str)
        }

        assertValid("en", Locale.ENGLISH)
        assertValid("en_US", Locale.US)
        assertValid("pl_PL", Locale("pl", "PL"))
    }

    @Test
    fun testUriDeserializer() {
        fun assertValid(str: String, uri: URI) {
            val decoderMock = mockk<Decoder>()
            every { decoderMock.decodeString() } returns str
            assertThat(UriSerializer.deserialize(decoderMock)).isEqualTo(uri)
        }

        assertValid("https://example.org", URI("https://example.org"))
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
    }
}