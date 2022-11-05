package info.skyblond.mc.mca.mixin;

import info.skyblond.mc.mca.MCAUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static info.skyblond.mc.mca.MinecraftChatAlternative.PEER;

@Mixin(MessageHandler.class)
public abstract class MixinMessageHandler {

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(SignedMessage message, MessageType.Parameters params, CallbackInfo ci) {
        var text = message.signedBody().content().plain();
        if (text.startsWith("mca:id:i2p:")) {
            var minecraft = MinecraftClient.getInstance();
            var networkHandler = minecraft.getNetworkHandler();
            if (networkHandler == null) {
                return;
            }
            var playerList = networkHandler.getPlayerList();
            if (playerList == null) {
                return;
            }
            // The uuid here might be fake uuid
            // search the player list to get username
            var username = playerList.stream()
                    .filter(it -> it.getProfile().getId().equals(message.signedHeader().sender()))
                    .map(it -> it.getProfile().getName())
                    .findAny().orElse(null);
            if (username == null) {
                return;
            }
            MCAUtils.runLater(() -> MCAUtils.tryConnectB32Address(
                    PEER, username, text.substring(11)));
            ci.cancel();
        }
    }
}
