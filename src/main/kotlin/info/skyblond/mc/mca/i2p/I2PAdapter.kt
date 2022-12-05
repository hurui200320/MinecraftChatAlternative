package info.skyblond.mc.mca.i2p

import info.skyblond.i2p.p2p.chat.I2PHelper
import info.skyblond.i2p.p2p.chat.core.*
import info.skyblond.i2p.p2p.chat.message.*
import info.skyblond.mc.mca.helper.ChatHelper
import info.skyblond.mc.mca.helper.MinecraftHelper
import info.skyblond.mc.mca.model.Lang
import io.netty.buffer.Unpooled
import kotlinx.serialization.json.Json
import mu.KLogger
import mu.KotlinLogging
import net.minecraft.client.MinecraftClient
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.encryption.PlayerPublicKey
import net.minecraft.network.encryption.PlayerPublicKey.PublicKeyData
import net.minecraft.text.Text
import java.time.Duration
import java.util.*
import java.util.concurrent.ThreadPoolExecutor
import kotlin.reflect.jvm.jvmName

/**
 * Encode [PlayerPublicKey.PublicKeyData] into base64
 */
private fun PlayerPublicKey.toBase64(): String {
    val byteBuf = Unpooled.buffer()
    this.data.write(PacketByteBuf(byteBuf))
    val bytes = ByteArray(byteBuf.readableBytes())
    byteBuf.readBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
}

/**
 * [PeerInfo] provider.
 * */
private fun createPeerInfo(peer: Peer<MCAContext>): PeerInfo {
    val (session, profileKeys) = MinecraftClient.getInstance()
        ?.let { it.session to it.profileKeys }
        ?: error("Minecraft instance is null")
    val (username, uuid) = session
        ?.let { (it.username ?: error("Cannot get current username")) to it.uuidOrNull }
        ?: error("Minecraft session is null")

    val (pubKey, signer) = profileKeys
        ?.let {
            (it.publicKey.orElse(null) ?: error("Cannot get player's public key")
                    ) to (it.signer ?: error("Cannot get signer"))
        }
        ?: error("Cannot get profile key")

    val info = mapOf<String, String>(
        PeerInfo.INFO_KEY_DEST to peer.getMyDestination().toBase64(),
        "minecraft.username" to username,
        "minecraft.profile_uuid" to (uuid?.toString() ?: error("Player's uuid is null")),
    )
    val dataToSign = PeerInfo.getDataForSign(info)
    val signature = signer.sign(dataToSign)
    return PeerInfo(
        info = info,
        signatureBase64 = Base64.getEncoder().encodeToString(signature),
        publicKeyBase64 = pubKey.toBase64()
    )
}

/**
 * Decode [PlayerPublicKey.PublicKeyData] from base64
 * */
private fun ByteArray.paresPublicKeyData(): PublicKeyData {
    val byteBuf = Unpooled.buffer(this.size)
    byteBuf.writeBytes(this)
    return PublicKeyData(PacketByteBuf(byteBuf))
}

private fun verifyPeerInfo(peerInfo: PeerInfo): Boolean {
    try {
        val profileUUID = peerInfo.info["minecraft.profile_uuid"]
            ?.let { UUID.fromString(it) } ?: return false
        val publicKeyData = peerInfo.getPublicKeyBytes().paresPublicKeyData()
        val pubKey = MinecraftClient.getInstance()?.let {
            PlayerPublicKey.verifyAndDecode(
                it.servicesSignatureVerifier,
                profileUUID, publicKeyData,
                PlayerPublicKey.EXPIRATION_GRACE_PERIOD
            )
        } ?: error("Minecraft instance is null")

        val verifier = pubKey.createSignatureInstance()
        val payload = peerInfo.getInfoBencodeBytes()
        val signature = peerInfo.getSignatureBytes()
        return verifier.validate(payload, signature)
    } catch (_: Throwable) {
        // if any error, invalid peer info
        return false
    }
}

/**
 * Get username from a *verified* [PeerInfo].
 * @see verifyPeerInfo
 */
fun PeerInfo.getUsername(): String = this.info["minecraft.username"] ?: error("Invalid peer info")

/**
 * Create a [IncomingMessageHandler].
 * @see IncomingMessageHandler
 * @see GeneralIncomingMessageHandler
 * */
fun createMessageHandler(
    logger: KLogger
): IncomingMessageHandler<MCAContext> = GeneralIncomingMessageHandler(
    // for AuthRequest
    createMessageHandlerWithReply { _, session, messageId, payload: AuthRequest, _ ->
        if (verifyPeerInfo(payload.peerInfo)) {
            val username = payload.peerInfo.getUsername()
            if (username in MinecraftHelper.getPlayerList()) {
                logger.info { "Authed ${session.getDisplayName()} as $username" }
                ChatHelper.addSystemMessageToChat(
                    Lang.TextMessage.SystemMessage.Connection.Incoming
                        .translate(Text.literal(payload.peerInfo.getUsername()))
                )
                AuthAcceptedReply(messageId)
            } else {
                // not in the same server
                AuthenticationFailedError("Not in the same server", messageId)
            }
        } else {
            AuthenticationFailedError("Verification failed", messageId)
        }
    },
    // for AuthAcceptedReply
    createMessageHandlerNoReply { _, session, _: AuthAcceptedReply, _ ->
        ChatHelper.addSystemMessageToChat(
            Lang.TextMessage.SystemMessage.Connection.Outgoing
                .translate(Text.literal(session.useContextSync { username }))
        )
    },
    // for PEX request
    createPEXRequestHandler(
        logger,
        filter = { verifyPeerInfo(it) },
        onException = { t, p -> logger.info(t) { "Failed to connect PEX discovered peer: $p" } }
    ),
    // for PEX reply
    createPEXReplyHandler(
        logger,
        filter = { verifyPeerInfo(it) },
        onException = { t, p -> logger.info(t) { "Failed to connect PEX discovered peer: $p" } }
    ),
    // for TextRequest
    createMessageHandlerWithReply { _, session, messageId, payload: TextMessageRequest, _ ->
        val username = session.useContextSync { username } ?: error("Unauthorized message")
        when (payload.scope) {
            "broadcast" -> ChatHelper.addBroadcastMessageToChat(
                username, Text.literal(payload.content)
            )

            "whisper" -> ChatHelper.addIncomingMessageToChat(
                username, Text.literal(payload.content)
            )

            else -> logger.warn { "Unknown message scope '${payload.scope}', content: ${payload.content}" }
        }
        NoContentReply(messageId)
    },
    // for ByeRequest
    createMessageHandlerNoReply { _, session, payload: ByeRequest, _ ->
        ChatHelper.addSystemMessageToChat(
            Lang.TextMessage.SystemMessage.Connection.Disconnected
                .translate(
                    Text.literal(session.getDisplayName()),
                    Text.literal(payload.reason)
                )
        )
    },
    // for NoContentReply
    createNoContentReplyHandler(),
    // For all errors
    createMessageHandlerNoReply { _, session, e: ErrorMessagePayload, _ ->
        if (session.useContextSync { isAuthed() }) {
            session.useContextSync { username }.let { username ->
                logger.warn { "$username replies an error: $e" }
                ChatHelper.addSystemMessageToChat(
                    Lang.TextMessage.SystemMessage.Error.PeerReportedError.translate(
                        Text.literal(username)
                    )
                )
            }
        }
    },
    // In case we're missing something
    createMessageHandlerNoReply { _, _, payload: MessagePayload, _ ->
        logger.error { "Missing handler for ${payload::class.jvmName}" }
        ChatHelper.addSystemMessageToChat(
            Lang.TextMessage.SystemMessage.Error.General.translate()
        )
    }
)

/**
 * Create a peer.
 * Do NOT use the same [destinationKey] during one run.
 * */
fun createPeer(
    json: Json, username: String, protocolName: String,
    destinationKey: ByteArray, threadPool: ThreadPoolExecutor,
    pexInterval: Duration
): Peer<MCAContext> {
    // each peer has its own logger
    val logger = KotlinLogging.logger("Peer#$username")
    // create manager
    val manager = I2PHelper.createSocketManager(destinationKey, "MinecraftChatAlternative")
    // handler
    val handler = createMessageHandler(logger)
    return Peer(
        json = json, socketManager = manager, applicationName = protocolName,
        sessionContextProvider = { MCAContext(it) },
        selfPeerInfoProvider = { createPeerInfo(it) },
        messageHandler = handler, threadPool = threadPool,
        pexInterval = pexInterval.toMillis(), logger = logger,
        onSessionSocketClose = { session ->
            session.useContextSync { this.username }?.let {
                // authed user
                logger.info { "Socket closed: $it" }
                ChatHelper.addSystemMessageToChat(
                    Lang.TextMessage.SystemMessage.Connection.SocketClosed
                        .translate(Text.literal(it))
                )
            }
        }
    )
}
