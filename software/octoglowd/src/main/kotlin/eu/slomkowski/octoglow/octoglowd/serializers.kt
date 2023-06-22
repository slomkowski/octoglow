package eu.slomkowski.octoglow.octoglowd

import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object LocalDateSerializer : KSerializer<LocalDate> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString()) // toString? format?
    }
}

object SimpleMonitorInstantSerializer : AbstractInstantSerializer2("""(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})([+-]\d{2}):(\d{2})""")

abstract class AbstractInstantSerializer2(private val pattern: String) : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InstantSerializer$pattern", PrimitiveKind.STRING)

    private val regex = Regex(pattern)

    override fun deserialize(decoder: Decoder): Instant {
        val str = decoder.decodeString()
        return checkNotNull(regex.matchEntire(str)?.let { match ->
            val (year, month, day, hour, minute, second, offsetHour, offsetMinute) = match.destructured
            LocalDateTime(
                year.toInt(),
                month.toInt(),
                day.toInt(),
                hour.toInt(),
                minute.toInt(),
                second.toInt()
            ).toInstant(UtcOffset(offsetHour.toInt(), offsetMinute.toInt()))
        }) { "$str does not match $pattern" }
    }

    override fun serialize(encoder: Encoder, value: Instant) = TODO()
}


object AirQualityInstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AirQualityInstantSerializer", PrimitiveKind.STRING)

    private val regex = Regex("""(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})""")

    override fun deserialize(decoder: Decoder): Instant {
        val str = decoder.decodeString()
        return checkNotNull(regex.matchEntire(str)?.let { match ->
            val (year, month, day, hour, minute, second) = match.destructured
            LocalDateTime(
                year.toInt(),
                month.toInt(),
                day.toInt(),
                hour.toInt(),
                minute.toInt(),
                second.toInt()
            ).toInstant(WARSAW_ZONE_ID)
        }) { "$str does not match $regex" }
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


