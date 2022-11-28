package info.skyblond.mc.mca.helper

import info.skyblond.mc.mca.helper.ChatHelper.shouldSendPlain
import info.skyblond.mc.mca.model.Lang
import mu.KotlinLogging
import net.minecraft.client.MinecraftClient
import net.minecraft.text.ClickEvent
import net.minecraft.text.Style
import net.minecraft.text.Text
import java.util.concurrent.ConcurrentHashMap

/**
 * Useful things about chat.
 * */
object ChatHelper {
    private val logger = KotlinLogging.logger { }

    /**
     * A place to store messages that should send through mc internal chat.
     * */
    @JvmStatic
    private val plainMessagePendingQueue = ConcurrentHashMap<String, Int>()

    private fun addPlainMessage(message: String) {
        plainMessagePendingQueue.compute(message) { _, oldCount ->
            // add 1 if is old message, otherwise count as 1 for new messages
            oldCount?.let { it + 1 } ?: 1
        }
    }

    /**
     * Query if we should send this message through mc internal chat.
     * @see info.skyblond.mc.mca.mixin.MixinClientPlayerEntity.onSendChatMessageInternal
     * */
    @JvmStatic
    fun shouldSendPlain(message: String): Boolean {
        var isFound = false
        plainMessagePendingQueue.compute(message) { _, oldCount ->
            oldCount?.let { count ->
                isFound = true
                if (count <= 1) {
                    // delete this
                    null
                } else {
                    // count down by 1
                    count - 1
                }
            }
        }
        return isFound
    }

    /**
     * Send a message using minecraft's internal chat.
     * Note: [net.minecraft.client.network.ClientPlayerEntity.sendChatMessage] will
     * be intercepted by the mixin [info.skyblond.mc.mca.mixin.MixinClientPlayerEntity.onSendChatMessageInternal]
     * and thus being sent through I2P. Using this method will add the message to queue
     * and make [shouldSendPlain] return true, to stop the chat hijack.
     * */
    @JvmStatic
    fun sendPlainMessage(message: String) {
        addPlainMessage(message)
        MinecraftClient.getInstance()?.player?.sendChatMessage(message, null)
            ?: run {// failed to send message
                logger.warn { "Failed to get current player, thus, failed to send message: $message" }
                shouldSendPlain(message) // remove the count
            }
    }

    /**
     * Add message to chat gui as-is.
     * */
    @JvmStatic
    fun addRawMessage(message: Text, narrateMessage: Text) {
        val minecraft = MinecraftClient.getInstance()
        if (minecraft.inGameHud != null) {
            minecraft.inGameHud.chatHud.addMessage(message)
        }
        minecraft.narratorManager.narrateChatMessage { narrateMessage }
    }

    /**
     * Add message as system message, like error message, or status, etc.
     * TODO: color for error (read), status (green for ok, etc.).
     * */
    @JvmStatic
    fun addSystemMessageToChat(content: Text) {
        val textChatComponent = Lang.ChatMessage.SystemMessage.Text
            .translate(content)
        val narrateChatComponent = Lang.ChatMessage.SystemMessage.Narrate
            .translate(content)
        addRawMessage(textChatComponent, narrateChatComponent)
    }

    /**
     * Add message as chat message.
     * */
    @JvmStatic
    fun addBroadcastMessageToChat(username: String, content: Text) {
        val textChatComponent = Lang.ChatMessage.I2PMessage.Broadcast.Text.translate(
            Text.literal(username).fillStyle(
                Style.EMPTY.withClickEvent(
                    ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        String.format("!dm %s ", username)
                    )
                )
            ), content
        )
        val narrateChatComponent = Lang.ChatMessage.I2PMessage.Broadcast.Narrate.translate(
            Text.literal(username), content
        )
        addRawMessage(textChatComponent, narrateChatComponent)
    }

    /**
     * Add incoming messages, like "someone whisper to you: ..."
     * */
    @JvmStatic
    fun addIncomingMessageToChat(username: String, content: Text) {
        val textChatComponent = Lang.ChatMessage.I2PMessage.Incoming.Text.translate(
            Text.literal(username).fillStyle(
                Style.EMPTY.withClickEvent(
                    ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        String.format("!dm %s ", username)
                    )
                )
            ), content
        )
        val narrateChatComponent = Lang.ChatMessage.I2PMessage.Incoming.Narrate.translate(
            Text.literal(username), content
        )
        addRawMessage(textChatComponent, narrateChatComponent)
    }

    /**
     * Add outgoing messages, like "you whisper to someone: ..."
     * */
    @JvmStatic
    fun addOutgoingMessageToChat(
        username: String,
        content: Text
    ) {
        val textChatComponent = Lang.ChatMessage.I2PMessage.Outgoing.Text.translate(
            Text.literal(username).fillStyle(
                Style.EMPTY
                    .withClickEvent(
                        ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            String.format("!dm %s ", username)
                        )
                    )
            ),
            content
        )
        val narrateChatComponent = Lang.ChatMessage.I2PMessage.Outgoing.Narrate.translate(
            Text.literal(username), content
        )
        addRawMessage(textChatComponent, narrateChatComponent)
    }
}
