package info.skyblond.mc.mca.model

import net.minecraft.text.Text
import net.minecraft.util.Language

private typealias MinecraftText = Text

/**
 * A tree-like structure represented translation keys with sealed classes.
 * */
sealed class Lang(
    vararg path: String
) {

    /**
     * Chat messages that will be shown on the screen.
     * */
    sealed class ChatMessage(
        vararg path: String
    ) : Lang("chat.mca", *path) {

        /**
         * System messages from mod, like status notifications.
         * */
        sealed class SystemMessage(
            vararg path: String
        ) : ChatMessage("system", *path) {

            /**
             * Template for showing text on screen
             * */
            object Text : SystemMessage()

            /**
             * Template for narrator to read out
             * */
            object Narrate : SystemMessage("narrate")

            fun translate(message: MinecraftText): MinecraftText =
                super.translate(message)
        }

        /**
         * I2P related messages, like chat messages
         * */
        sealed class I2PMessage(
            vararg path: String
        ) : ChatMessage("i2p", *path) {

            /**
             * Public messages
             * */
            sealed class Broadcast(
                vararg path: String
            ) : I2PMessage("broadcast", *path) {

                /**
                 * Template for showing text on screen
                 * */
                object Text : Broadcast()

                /**
                 * Template for narrator to read out
                 * */
                object Narrate : Broadcast("narrate")

                fun translate(username: MinecraftText, content: MinecraftText): MinecraftText =
                    super.translate(username, content)
            }

            /**
             * Incoming messages, someone whisper to you
             * */
            sealed class Incoming(
                vararg path: String
            ) : I2PMessage("incoming", *path) {

                /**
                 * Template for showing text on screen
                 * */
                object Text : Incoming()

                /**
                 * Template for narrator to read out
                 * */
                object Narrate : Incoming("narrate")

                fun translate(username: MinecraftText, content: MinecraftText): MinecraftText =
                    super.translate(username, content)
            }

            /**
             * Outgoing messages, you whisper to someone
             * */
            sealed class Outgoing(
                vararg path: String
            ) : I2PMessage("outgoing", *path) {

                /**
                 * Template for showing text on screen
                 * */
                object Text : Outgoing()

                /**
                 * Template for narrator to read out
                 * */
                object Narrate : Outgoing("narrate")

                fun translate(username: MinecraftText, content: MinecraftText): MinecraftText =
                    super.translate(username, content)
            }
        }
    }

    /**
     * Part of the [ChatMessage]
     * */
    sealed class TextMessage(
        vararg path: String
    ) : Lang("text.mca", *path) {

        /**
         * For [ChatMessage.SystemMessage]
         * */
        sealed class SystemMessage(
            vararg path: String
        ) : TextMessage("system", *path) {
            /**
             * Connection related, like someone disconnected
             * */
            sealed class Connection(
                vararg path: String
            ) : SystemMessage("connection", *path) {
                /**
                 * Someone connected us
                 * */
                object Incoming : Connection("incoming") {
                    fun translate(username: MinecraftText): MinecraftText =
                        super.translate(username)
                }

                /**
                 * We connected to someone
                 * */
                object Outgoing : Connection("outgoing") {
                    fun translate(username: MinecraftText): MinecraftText =
                        super.translate(username)
                }

                /**
                 * Someone disconnected us
                 * */
                object Disconnected : Connection("disconnected") {
                    fun translate(username: MinecraftText, reason: MinecraftText): MinecraftText =
                        super.translate(username, reason)
                }

                /**
                 * Someone's connection closed
                 * */
                object SocketClosed : Connection("socket_closed") {
                    fun translate(username: MinecraftText): MinecraftText =
                        super.translate(username)
                }
            }

            /**
             * Error related, like some part of system crashed
             * */
            sealed class Error(
                vararg path: String
            ) : SystemMessage("error", *path) {
                /**
                 * General message, saying there is an error, check out the log.
                 * */
                object General : Error("general") {
                    fun translate(): MinecraftText = super.translate()
                }

                /**
                 * The peer should never stop. But if that happened, use this to notify
                 * player.
                 * */
                object PeerStopped : Error("peer_stopped") {
                    fun translate(): MinecraftText = super.translate()
                }

                /**
                 * The peer should never stop. But if that happened, use this to notify
                 * player.
                 * */
                object PeerReportedError : Error("on_error_reply") {
                    fun translate(username: MinecraftText): MinecraftText =
                        super.translate(username)
                }
            }
        }
    }

    private val fullPath: String = path.joinToString(".")

    protected fun translate(vararg args: Any): MinecraftText {
        require(Language.getInstance().get(fullPath) != fullPath) { "Missing translation key: $fullPath" }
        return MinecraftText.translatable(fullPath, *args)
    }
}
