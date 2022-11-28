package info.skyblond.mc.mca.mixin;

import info.skyblond.mc.mca.ClientModEntry;
import info.skyblond.mc.mca.helper.ChatHelper;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MessageHandler.class)
public abstract class MixinMessageHandler {

    @Inject(method = "onChatMessage", at = @At("RETURN"))
    private void onChatMessage(SignedMessage message, MessageType.Parameters params, CallbackInfo ci) {
        var text = message.signedBody().content().plain();
        if (text.contains(".b32.i2p")) {
            // the b32 address: {52 chars}.b32.i2p
            var prefix = text.split("\\.b32\\.i2p")[0];
            if (prefix.length() >= 52) {
                var b32 = prefix.substring(prefix.length() - 52) + ".b32.i2p";
                if (b32.equals(ClientModEntry.getPeer().getMyB32Address())) {
                    // is ourselves
                    return;
                }
                ChatHelper.addSystemMessageToChat(Text.translatable(
                        "%s sent a b32 address in chat, %s",
                        params.name(),
                        Text.literal("click [here] to connect").fillStyle(Style.EMPTY.withClickEvent(
                                new ClickEvent(
                                        ClickEvent.Action.SUGGEST_COMMAND,
                                        "!connect " + b32
                                )
                        ))
                ));
            }
        }
    }
}
