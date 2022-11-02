package info.skyblond.mc.mca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.skyblond.mc.mca.i2p.chat.I2PChatMessage;
import info.skyblond.mc.mca.i2p.chat.I2PChatPeer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class MinecraftChatAlternative implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MinecraftChatAlternative");
    private static final Gson GSON = new GsonBuilder().create();
    public static final I2PChatPeer<String, I2PChatMessage> PEER =
            new I2PChatPeer<>(
                    s -> GSON.fromJson(s, I2PChatMessage.class), GSON::toJson
            );

    @Override
    public void onInitializeClient() {
        LOGGER.info("Hello!");
        PEER.getMessageReceiver().addInComingMessageListener((message, source) -> {
            var sourceProfile = PEER.resolveSource(source);
            if (sourceProfile != null) {
                switch (message.action().toLowerCase()) {
                    case "broadcast" -> addBroadcastMessageToChat(
                            new MCAPlatform("I2P", "i2p"),
                            // TODO json to Component?
                            sourceProfile, Component.literal(message.payload()));
                    case "whisper" -> {
                        // TODO
                    }
                    default -> LOGGER.warn("Unknown message action '{}'", message.action());
                }
            } else {
                LOGGER.warn("Unknown message source '{}'", source.getHash().toBase64());
            }
        });

        var self = Minecraft.getInstance().getUser().getName();
        // connect to self, so we can receive our own message
        if (!PEER.connect(self, PEER.getMessageReceiver().getMyDestination())) {
            throw new IllegalStateException("Failed to make self connection");
        }
        LOGGER.info("I2P self dest: " + PEER.getMessageReceiver().getMyDestination().toBase64());
    }

    // TODO in coming and out going messages?
    public static void addBroadcastMessageToChat(
            MCAPlatform platform,
            String username,
            Component content
    ) {
        var textChatComponent = Component.translatable(
                "chat.type.mca.text", // platform, player, content
                Component.literal(platform.platformDisplayName()),
                Component.literal(username),
                content
        ).withStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        // TODO implement command system
                        String.format("!msg %s %s ", platform.platformCodeName(), username)
                )));

        var narrateChatComponent = Component.translatable(
                "chat.type.mca.text.narrate", // player, platform, content
                Component.literal(username),
                Component.literal(platform.platformDisplayName()),
                content
        );

        var minecraft = Minecraft.getInstance();
        minecraft.gui.getChat().addMessage(textChatComponent);
        minecraft.getNarrator().sayChatNow(() -> narrateChatComponent);
    }
}
