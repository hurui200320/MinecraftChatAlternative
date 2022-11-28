package info.skyblond.mc.mca.mixin;

import info.skyblond.i2p.p2p.chat.message.TextMessageRequest;
import info.skyblond.mc.mca.ClientModEntry;
import info.skyblond.mc.mca.helper.ChatHelper;
import info.skyblond.mc.mca.helper.CommandHelper;
import info.skyblond.mc.mca.helper.MinecraftHelper;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity {

    @Inject(method = "sendChatMessageInternal", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessageInternal(String message, Text preview, CallbackInfo ci) {

        if (ChatHelper.shouldSendPlain(message)) {
            // this message should be sent through minecraft's internal chat
            // stop further processing
            return;
        }

        if (CommandHelper.isCommand(message)) {
            CommandHelper.handleCommand(message);
        } else {
            // broadcast message
            var playerList = MinecraftHelper.getPlayerList();
            ClientModEntry.getPeer().sendRequest(
                    new TextMessageRequest("broadcast", message),
                    s -> s.useContextSync(c -> c.getUsername() != null && playerList.contains(c.getUsername()))
            );
            // show the message to ourselves
            ChatHelper.addBroadcastMessageToChat(
                    Objects.requireNonNull(MinecraftHelper.getCurrentUsername()), Text.literal(message)
            );
        }

        // cancel sending
        ci.cancel();
    }
}
