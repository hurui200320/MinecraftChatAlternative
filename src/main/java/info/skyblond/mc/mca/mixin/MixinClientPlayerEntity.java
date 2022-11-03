package info.skyblond.mc.mca.mixin;

import info.skyblond.mc.mca.model.MCAMessage;
import info.skyblond.mc.mca.model.MCAPlatform;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static info.skyblond.mc.mca.MinecraftChatAlternative.*;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity {

    @Inject(method = "sendChatMessageInternal", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessageInternal(String message, Text preview, CallbackInfo ci) {
        LOGGER.info("Intercepted chat message: {}", message);

        if (message.startsWith("!")) {
            // is a command
            var firstSpace = message.indexOf(' ');
            if (firstSpace == -1) {
                firstSpace = message.length();
            }
            var minecraft = MinecraftClient.getInstance();

            switch (message.substring(0, firstSpace)) {
                case "!gen" -> {
                    minecraft.keyboard.setClipboard(String.format("!connect %s %s",
                                    minecraft.getSession().getUsername(),
                                    "mca:id:i2p:" + PEER.getMyDestination().toBase64()
                            )
                    );
                    addSystemMessageToChat(MCAPlatform.MCA,
                            Text.literal("Command copied to clipboard"));
                }
                case "!connect" -> {
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
                                    addSystemMessageToChat(MCAPlatform.MCA,
                                            Text.literal("Connected to " + username));
                                } else {
                                    // failed
                                    addSystemMessageToChat(MCAPlatform.MCA,
                                            Text.literal("Connection rejected by " + username));
                                    LOGGER.error("Failed to connect {} at {}", username, dest.toBase64());
                                }
                            } catch (DataFormatException ignored) {
                                addSystemMessageToChat(MCAPlatform.MCA,
                                        Text.literal("Bad id!"));
                            }
                        } else {
                            addSystemMessageToChat(MCAPlatform.MCA,
                                    Text.literal("Bad id!"));
                        }
                    } else {
                        addSystemMessageToChat(MCAPlatform.MCA,
                                Text.literal("Bad command!"));
                    }
                }
                default -> addSystemMessageToChat(MCAPlatform.MCA,
                        Text.literal("Unknown command!"));
            }
        } else {
            // Do not block ui actions
            var t = new Thread(() -> {
                var failedList = PEER.sendMessage(new MCAMessage("broadcast", message));
                failedList.forEach(u -> addSystemMessageToChat(MCAPlatform.MCA,
                        Text.literal("Failed to send message to " + u)));
            });
            t.setDaemon(true);
            t.start();
        }
        // cancel sending, but how to show it on screen?
        ci.cancel();
    }
}
