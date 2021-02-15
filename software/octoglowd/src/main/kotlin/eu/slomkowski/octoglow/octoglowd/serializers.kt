package eu.slomkowski.octoglow.octoglowd

import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.format.DateTimeFormatter

object LocalDateSerializer : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString()) // toString? format?
    }
}

abstract class AbstractInstantSerializer(pattern: String) : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InstantSerializer$pattern", PrimitiveKind.STRING)

    private val datetimeFormatter = DateTimeFormatter.ofPattern(pattern)

    override fun deserialize(decoder: Decoder): Instant =
        java.time.ZonedDateTime.parse(decoder.decodeString(), datetimeFormatter).toInstant().toKotlinInstant()

    override fun serialize(encoder: Encoder, value: Instant) = TODO()
}

object SimpleMonitorInstantSerializer : AbstractInstantSerializer("yyyy-MM-dd HH:mm:ssxxx")

object AirQualityInstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AirQualityInstantSerializer", PrimitiveKind.STRING)

    private val datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val timeZone = WARSAW_ZONE_ID.toKotlinTimeZone()

    override fun deserialize(decoder: Decoder): Instant {
        val ldt = java.time.LocalDateTime.parse(decoder.decodeString(), datetimeFormatter).toKotlinLocalDateTime()
        return ldt.toInstant(timeZone)
    }

    override fun serialize(encoder: Encoder, value: Instant) = TODO()
}

object InstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString()) // toString? format?
    }
}


