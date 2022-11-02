package info.skyblond.mc.mca.mixin;

import info.skyblond.mc.mca.MinecraftChatAlternative;
import info.skyblond.mc.mca.i2p.chat.I2PChatMessage;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static info.skyblond.mc.mca.MinecraftChatAlternative.PEER;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {


    @Shadow
    @Final
    public static Logger LOGGER;

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, Component preview, CallbackInfo ci) {
        MinecraftChatAlternative.LOGGER.info("Intercepted chat message: {}", message);

        // skip if id
        if (message.startsWith("mca:id:i2p:")) {
            return;
        }

        if (message.startsWith("!")) {
            var minecraft = Minecraft.getInstance();
            if (message.equals("!gen")) {
                minecraft.keyboardHandler.setClipboard(String.format("!connect %s %s",
                                minecraft.getUser().getName(),
                                "mca:id:i2p:" + PEER.getMessageReceiver().getMyDestination().toBase64()
                        )
                );
                minecraft.getChatListener().handleSystemMessage(
                        Component.literal("[I2P] Command copied to clipboard"), false);

            } else if (message.startsWith("!connect ")) {
                var t = message.split(" ");
                if (t.length == 3) {
                    var username = t[1];
                    var rawMCAId = t[2];
                    if (rawMCAId.startsWith("mca:id:i2p:")) {
                        try {
                            LOGGER.info(rawMCAId.substring(11));
                            var dest = new Destination(rawMCAId.substring(11));
                            if (PEER.connect(username, dest)) {
                                // connect ok, add chat message
                                minecraft.getChatListener().handleSystemMessage(
                                        Component.literal("[I2P] Connected to " + username), false);
                            } else {
                                // failed
                                minecraft.getChatListener().handleSystemMessage(
                                        Component.literal("[I2P] Connection rejected by " + username), false);
                                LOGGER.error("Failed to connect {} at {}", username, dest.toBase64());
                            }
                        } catch (DataFormatException ignored) {
                            minecraft.getChatListener().handleSystemMessage(
                                    Component.literal("[I2P] Bad id!"), false);
                        }
                    } else {
                        minecraft.getChatListener().handleSystemMessage(
                                Component.literal("[I2P] Bad id!"), false);
                    }
                } else {
                    minecraft.getChatListener().handleSystemMessage(
                            Component.literal("[I2P] Bad command!"), false);
                }
            }
        } else {
            PEER.sendMessage(new I2PChatMessage("broadcast", message));
        }
        // cancel sending, but how to show it on screen?
        ci.cancel();
    }
}
