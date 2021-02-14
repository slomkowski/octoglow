package eu.slomkowski.octoglow.octoglowd

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object LocalDateSerializer : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString()) // toString? format?
    }
}

object SimpleMonitorInstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SimpleMonitorInstant", PrimitiveKind.STRING)

    private val datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxxx")

    override fun deserialize(decoder: Decoder): Instant =
        ZonedDateTime.parse(decoder.decodeString(), datetimeFormatter).toInstant().toKotlinInstant()

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString()) // toString? format?
    }
}

object InstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString()) // toString? format?
    }
}


