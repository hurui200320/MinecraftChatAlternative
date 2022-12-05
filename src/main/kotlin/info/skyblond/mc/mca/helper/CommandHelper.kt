package info.skyblond.mc.mca.helper

import info.skyblond.i2p.p2p.chat.core.SessionSource
import info.skyblond.i2p.p2p.chat.message.TextMessageRequest
import info.skyblond.mc.mca.ClientModEntry
import info.skyblond.mc.mca.helper.ChatHelper.sendPlainMessage
import info.skyblond.mc.mca.model.ConfigFile
import info.skyblond.mc.mca.model.MCACommand
import mu.KotlinLogging
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

/**
 * Useful things about commands
 * */
object CommandHelper {
    private val logger = KotlinLogging.logger { }
    private const val commandPrefix = "!"
    private val commands: Set<MCACommand> = buildSet {
        registerCommand(
            commandName = "mc",
            description = "send your chat using minecraft's og chat",
            parameterList = listOf("message" to "The message you want to send")
        ) {
            MinecraftHelper.runLater {
                if (it.isNotBlank()) {
                    sendPlainMessage(it)
                } // ignore if message is empty
            }
        }
        // generate connect str
        registerCommand(
            commandName = "gen",
            description = "generate connect command",
            parameterList = listOf()
        ) {
            MinecraftClient.getInstance().keyboard.clipboard =
                String.format(
                    "!connect %s",
                    ClientModEntry.getPeer().getMyDestination().toBase32()
                )
            ChatHelper.addSystemMessageToChat(
                Text.literal("Command copied to clipboard")
            )
        }
        // connect
        registerCommand(
            commandName = "connect",
            description = "connect to other MCA users",
            parameterList = listOf("address" to "Other player's address")
        ) { raw ->
            val args = raw.split(" ").filter { it.isNotBlank() }
            if (args.size == 1) {
                MinecraftHelper.runLater {
                    try {
                        ChatHelper.addSystemMessageToChat(
                            Text.literal("Connecting...")
                        )
                        ClientModEntry.getPeer().connect(args[0])
                        ChatHelper.addSystemMessageToChat(
                            Text.literal("Connected to ${args[0]}")
                        )
                    } catch (t: Throwable) {
                        ChatHelper.addSystemMessageToChat(
                            Text.literal("Failed to connect to ${args[0]}: ${t.message}")
                        )
                    }
                }
            } else {
                ChatHelper.addSystemMessageToChat(
                    Text.literal("This command need 1 parameter")
                )
            }
        }
        // i2p dm
        registerCommand(
            commandName = "dm",
            description = "send direct message using MCA",
            parameterList = listOf(
                "username" to "The player you want to dm",
                "message" to "The message you want to send"
            )
        ) { raw ->
            val firstSpaceIndex = raw.indexOf(' ')
            if (firstSpaceIndex == -1) {
                ChatHelper.addSystemMessageToChat(
                    Text.literal("This command need 2 parameters")
                )
            } else {
                val target = raw.substring(0, firstSpaceIndex)
                if (target in MinecraftHelper.getPlayerList()) {
                    val rest = raw.substring(firstSpaceIndex + 1)
                    if (rest.isNotBlank()) {
                        ClientModEntry.getPeer().sendRequest(
                            TextMessageRequest(
                                "whisper", rest
                            )
                        ) {
                            target == it.useContextSync { username }
                        }
                        ChatHelper.addOutgoingMessageToChat(target, Text.literal(rest))
                    } else {
                        ChatHelper.addSystemMessageToChat(
                            Text.literal("You can't send empty message to others")
                        )
                    }
                } else {
                    ChatHelper.addSystemMessageToChat(
                        Text.literal("Player $target not found")
                    )
                }
            }
        }

        registerCommand(
            path = "i2p",
            description = "I2P related operations"
        ) {
            registerCommand(
                commandName = "regen",
                description = "generate a new i2p identity",
                parameterList = listOf()
            ) {
                ConfigFile.ModConfig.use { this.discardI2PKey() }
                ConfigFile.ModConfig.save()
                ChatHelper.addSystemMessageToChat(
                    Text.literal("Done. Please restart your game.")
                )
            }
            registerCommand(
                commandName = "status",
                description = "show current I2P peer status",
                parameterList = listOf()
            ) {
                val sessions = ClientModEntry.getPeer().dumpSessions()

                ChatHelper.addSystemMessageToChat(
                    Text.literal(
                        "Current connecting ${sessions.size} players. They're: " +
                                sessions.mapNotNull {
                                    it.useContextSync {
                                        username?.plus(
                                            "(${
                                                when (sessionSource) {
                                                    SessionSource.CLIENT -> "outgoing"
                                                    SessionSource.SERVER -> "incoming"
                                                }
                                            })"
                                        )
                                    }
                                }
                                    .joinToString(", ")
                    )
                )
                ChatHelper.addSystemMessageToChat(
                    // TODO give some more details about current peer's status
                    Text.literal("Some status here!")
                )
            }
        }


        registerCommand(
            commandName = "foo",
            description = "test",
            parameterList = listOf()
        ) {
            logger.info { "Bar!" }
        }
    }

    private fun MutableSet<MCACommand>.registerCommand(
        commandName: String,
        description: String,
        parameterList: List<Pair<String, String>>,
        action: (String) -> Unit
    ) = require(
        this.add(
            MCACommand(
                commandName = commandName,
                description = description,
                parameterList = parameterList,
                actionInternal = action
            )
        )
    ) { "Duplicated command name: $commandName" }

    private fun MutableSet<MCACommand>.registerCommand(
        path: String,
        description: String,
        block: MutableSet<MCACommand>.() -> Unit
    ) = require(
        this.add(
            MCACommand(
                commandName = path,
                description = description,
                subCommands = buildSet(block)
            )
        )
    ) { "Duplicated command name: $path" }

    /**
     * Decide if the message is a command.
     * @return true if this is a command and should be handled by [handleCommand].
     * */
    @JvmStatic
    fun isCommand(message: String): Boolean =
        message.startsWith(commandPrefix)

    /**
     * Handle a command. Do nothing if not a command.
     * @see [isCommand]
     * */
    @JvmStatic
    fun handleCommand(rawMessage: String) {
        if (!isCommand(rawMessage)) return
        val message = rawMessage.removePrefix(commandPrefix)
        // find first word
        var firstSpace: Int = message.indexOf(' ')
        if (firstSpace == -1) {
            firstSpace = message.length
        }
        val command = message.substring(0, firstSpace)

        if (command == "help") {
            val path = mutableListOf<MCACommand>()
            var searchScope = commands.toList()
            var rest = if (firstSpace >= message.length) "" else message.substring(firstSpace + 1)
            while (searchScope.isNotEmpty() && rest.isNotEmpty()) {
                val c = searchScope.filter { rest.startsWith(it.commandName) }.maxByOrNull { it.commandName.length }
                if (c != null) {
                    path.add(c)
                    searchScope = c.subCommands.toList()
                    rest = rest.substring(c.commandName.length).trim()
                } else {
                    val commandFullPath = path.joinToString(separator = " ", prefix = commandPrefix) { it.commandName }
                    ChatHelper.addSystemMessageToChat(
                        Text.literal(
                            "Command $commandFullPath don't have sub command ${rest.split(" ")[0]}"
                        )
                    )
                    return
                }
            }
            val commandFullPath = path.joinToString(separator = " ", prefix = commandPrefix) { it.commandName }
            if (path.isEmpty()) {
                // a !help with no parameter
                ChatHelper.addSystemMessageToChat(Text.literal(
                    "Commands:\n" + commands.joinToString("\n") {
                        "    " + it.commandName + ": " + it.description
                    }
                ))
            } else {
                if (path.last().subCommands.isNotEmpty()) {
                    ChatHelper.addSystemMessageToChat(Text.literal(
                        "Sub commands for $commandFullPath:\n" +
                                path.last().subCommands.joinToString("\n") {
                                    "    ${it.commandName}: ${it.description}"
                                }
                    ))
                } else {
                    val c = path.last()
                    ChatHelper.addSystemMessageToChat(
                        Text.literal(
                            "Command $commandFullPath ${
                                c.parameterList.joinToString(" ") {
                                    "<${it.first}>"
                                }
                            }: " + c.description + "\n" +
                                    c.parameterList.joinToString("\n") {
                                        "    ${it.first}: ${it.second}"
                                    }
                        )
                    )
                }
            }
        } else {
            commands.find { it.commandName == command }
                ?.let { it(message.substring(firstSpace).trimStart()) }
                ?: run {
                    ChatHelper.addSystemMessageToChat(Text.literal("Unknown command: $command"))
                }
        }
    }
}
