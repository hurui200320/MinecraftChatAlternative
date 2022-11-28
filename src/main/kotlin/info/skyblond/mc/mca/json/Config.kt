package info.skyblond.mc.mca.json

import info.skyblond.i2p.p2p.chat.I2PHelper
import kotlinx.serialization.Serializable
import net.i2p.data.Destination
import java.time.Duration
import java.time.ZonedDateTime

@Serializable
class MCAConfig(
    @Serializable(with = ByteArrayAsBase64Serializer::class)
    private var _i2pKey: ByteArray = I2PHelper.generateDestinationKey(),
    /**
     * Duration between announce PEX
     * */
    @Serializable(with = DurationSerializer ::class)
    var pexInterval: Duration = Duration.ofSeconds(45),
    /**
     * Duration between fetch player list and update last seen
     * */
    @Serializable(with = DurationSerializer::class)
    var lastSeenUpdateInterval: Duration = Duration.ofMinutes(3),
    /**
     * Duration between remove invalid session
     * */
    @Serializable(with = DurationSerializer::class)
    var sessionRemoverInterval: Duration = Duration.ofMinutes(2),
    /**
     * Duration between connect peers from peer repo
     * */
    @Serializable(with = DurationSerializer::class)
    var autoConnectInterval: Duration = Duration.ofSeconds(15),
) {
    val i2pDestinationKey
        get() = _i2pKey

    /**
     * Discard the existing [i2pDestinationKey] and generate a new one
     * */
    fun discardI2PKey() {
        _i2pKey = I2PHelper.generateDestinationKey()
    }
}

@Serializable
class PeerRepoEntry(
    @Serializable(with = DestinationSerializer::class)
    var destination: Destination,
    @Serializable(with = ZonedDateTimeAsTimestampSerializer::class)
    private var _lastSeen: ZonedDateTime
) {
    val lastSeen
        get() = _lastSeen

    fun seen(dest: Destination) {
        destination = dest
        _lastSeen = ZonedDateTime.now()
    }
}
