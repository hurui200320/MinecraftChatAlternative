package info.skyblond.mc.mca.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.i2p.data.Destination
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

/**
 * Treat [ByteArray] as base64 encoding
 * */
object ByteArrayAsBase64Serializer : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("ByteArrayAsBase64", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ByteArray =
        Base64.getDecoder().decode(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: ByteArray) =
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
}

/**
 * Handle [Destination].
 * */
object DestinationSerializer : KSerializer<Destination> {
    override val descriptor = PrimitiveSerialDescriptor("Destination", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Destination =
        Destination(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Destination) =
        encoder.encodeString(value.toBase64())
}

/**
 * Encode the [ZonedDateTime] as unix timestamp, so we don't care time zones.
 * */
object ZonedDateTimeAsTimestampSerializer : KSerializer<ZonedDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("ZonedDateTime", PrimitiveKind.LONG)

    // People won't change their timezone when playing minecraft, right?
    override fun deserialize(decoder: Decoder): ZonedDateTime =
        Instant.ofEpochSecond(decoder.decodeLong()).atZone(ZoneId.systemDefault())

    override fun serialize(encoder: Encoder, value: ZonedDateTime) =
        encoder.encodeLong(value.toInstant().epochSecond)
}

object DurationSerializer : KSerializer<Duration> {
    override val descriptor = PrimitiveSerialDescriptor("DurationAsString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Duration =
        Duration.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: Duration) =
        encoder.encodeString(value.toString())
}
