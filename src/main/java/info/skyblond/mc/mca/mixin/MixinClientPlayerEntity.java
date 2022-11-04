package info.skyblond.mc.mca.mixin;

import info.skyblond.mc.mca.MCAUtils;
import info.skyblond.mc.mca.model.MCAPlatform;
import info.skyblond.mc.mca.model.MessagePayload;
import net.i2p.I2PAppContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static info.skyblond.mc.mca.MinecraftChatAlternative.PEER;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity {

    @Shadow
    @Final
    public static Logger LOGGER;

    @Inject(method = "sendChatMessageInternal", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessageInternal(String message, Text preview, CallbackInfo ci) {
        LOGGER.info("Intercepted chat message: {}", message);

        if (message.startsWith("mca:id")) {
            return;
        }

        if (message.startsWith("!")) {
            // is a command
            var firstSpace = message.indexOf(' ');
            if (firstSpace == -1) {
                firstSpace = message.length();
            }
            var minecraft = MinecraftClient.getInstance();

            switch (message.substring(0, firstSpace)) {
                case "!foo" -> {
                    var b32Address = PEER.getMyDestination().toBase32();
                    var dest = I2PAppContext.getGlobalContext().namingService().lookup(b32Address);
                    MCAUtils.addSystemMessageToChat(MCAPlatform.MCA, Text.literal(
                            dest.equals(PEER.getMyDestination()) + ""));
                }
                case "!expose" -> {
                    if (minecraft.player != null) {
                        minecraft.player.sendChatMessage("mca:id:i2p:" + PEER.getMyDestination().toBase32(), null);
                    }
                }
                case "!gen" -> {
                    minecraft.keyboard.setClipboard(String.format("!connect %s %s",
                                    MCAUtils.getCurrentUsername(),
                                    "mca:id:i2p:" + PEER.getMyDestination().toBase32()
                            )
                    );
                    MCAUtils.addSystemMessageToChat(MCAPlatform.MCA,
                            Text.literal("Command copied to clipboard"));
                }
                case "!connect" -> {
                    var t = message.split(" ");
                    if (t.length == 3) {
                        var username = t[1];
                        var rawMCAId = t[2];
                        if (rawMCAId.startsWith("mca:id:i2p:")) {
                            MCAUtils.runLater(() -> {
                                var reason = MCAUtils.tryConnectB32Address(PEER,
                                        username, rawMCAId.substring(11));
                                if (reason != null) {
                                    MCAUtils.addSystemMessageToChat(MCAPlatform.MCA,
                                            Text.literal(reason));
                                }
                            });
                        } else {
                            MCAUtils.addSystemMessageToChat(MCAPlatform.MCA,
                                    Text.literal("Bad id!"));
                        }
                    } else {
                        MCAUtils.addSystemMessageToChat(MCAPlatform.MCA,
                                Text.literal("Bad command! Expect: !connect <username> <dest>"));
                    }
                }
                case "!msg" -> {
                    var t = message.split(" ");
                    if (t.length == 4) {
                        var platformCodeName = t[1];
                        var username = t[2];
                        var content = t[3];
                        if (platformCodeName.equals("i2p")) {
                            MCAUtils.runLater(() -> {
                                try {
                                    if (PEER.sendMessage(new MessagePayload("whisper", content), username).get()) {
                                        MCAUtils.addOutgoingMessageToChat(MCAPlatform.I2P, username, Text.literal(content));
                                    } else {
                                        MCAUtils.addSystemMessageToChat(MCAPlatform.I2P, Text.literal(
                                                "Failed to send message to " + username));
                                    }
                                } catch (InterruptedException | ExecutionException e) {
                                    LOGGER.error("Failed to send message to " + username, e);
                                    MCAUtils.addSystemMessageToChat(MCAPlatform.I2P, Text.literal(
                                            "Failed to send message to " + username + ": " + e.getMessage()));
                                }
                            });
                        } else {
                            MCAUtils.addSystemMessageToChat(MCAPlatform.MCA,
                                    Text.literal("Unsupported platform. Currently only support i2p"));
                        }
                    } else {
                        MCAUtils.addSystemMessageToChat(MCAPlatform.MCA,
                                Text.literal("Bad command! Expect: !msg <platform> <username> <content>"));
                    }
                }
                default -> MCAUtils.addSystemMessageToChat(MCAPlatform.MCA,
                        Text.literal("Unknown command!"));
            }
        } else {
            // Do not block ui actions
            MCAUtils.runLater(() -> {
                List<String> failedList = PEER.broadcastMessage(message);
                if (failedList == null) {
                    MCAUtils.addSystemMessageToChat(MCAPlatform.I2P,
                            Text.literal("Failed to send message: Player list not found"));
                } else if (!failedList.isEmpty()) {
                    MCAUtils.addSystemMessageToChat(MCAPlatform.I2P,
                            Text.literal("Failed to send message to " +
                                    failedList.stream().reduce("", (a, b) ->
                                            a.isBlank() ? a + b : a + ", " + b)));
                }
            });
        }
        // cancel sending, but how to show it on screen?
        ci.cancel();
    }
}
