package info.skyblond.mc.mca.model

import info.skyblond.mc.mca.helper.ChatHelper
import net.minecraft.text.Text

class MCACommand(
    val commandName: String,
    val description: String,
    val parameterList: List<Pair<String, String>> = emptyList(),
    val subCommands: Set<MCACommand> = emptySet(),
    _action: ((String) -> Unit)? = null
) {
    private val action: (String) -> Unit = _action ?: { message ->
        var firstSpace: Int = message.indexOf(' ')
        if (firstSpace == -1) {
            firstSpace = message.length
        }
        val command = message.substring(0, firstSpace)

        subCommands.find { it.commandName == command }
            ?.let { it(message.substring(firstSpace).trimStart()) }
            ?: run {
                ChatHelper.addSystemMessageToChat(Text.literal("Unknown subcommand: $command"))
            }
    }

    init {
        require(!commandName.contains(" ")) { "Command name contains space" }
        require(commandName != "help") { "Command help is a internal command" }
        if (_action == null) {
            // contains sub command
            require(subCommands.isNotEmpty()) { "Commands without action should have subcommands" }
        }
    }

    operator fun invoke(parameter: String) = action.invoke(parameter)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MCACommand) return false

        if (commandName != other.commandName) return false

        return true
    }

    override fun hashCode(): Int {
        return commandName.hashCode()
    }

}
