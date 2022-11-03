package info.skyblond.mc.mca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import info.skyblond.mc.mca.i2p.I2PChatPeer;
import info.skyblond.mc.mca.model.MCAPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class MinecraftChatAlternative implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MinecraftChatAlternative");
    private static final Gson GSON = new GsonBuilder().create();
    public static final I2PChatPeer PEER = new I2PChatPeer(GSON);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Hello!");

        var self = MinecraftClient.getInstance().getSession().getUsername();
        // connect to self, so we can receive our own message
        if (!PEER.connect(self, PEER.getMyDestination())) {
            throw new IllegalStateException("Failed to make self connection");
        }
        LOGGER.info("I2P self dest: " + PEER.getMyDestination().toBase64());
    }

    // TODO in coming and out going messages?
    public static void addBroadcastMessageToChat(
            MCAPlatform platform,
            String username,
            Text content
    ) {
        var textChatComponent = Text.translatable(
                "chat.type.mca.text", // platform, player, content
                Text.literal(platform.platformDisplayName()),
                Text.literal(username),
                content
        ).fillStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        // TODO implement command system
                        String.format("!msg %s %s ", platform.platformCodeName(), username)
                )));

        var narrateChatComponent = Text.translatable(
                "chat.type.mca.text.narrate", // player, platform, content
                Text.literal(username),
                Text.literal(platform.platformDisplayName()),
                content
        );

        addMessage(textChatComponent, narrateChatComponent);
    }

    public static void addSystemMessageToChat(
            MCAPlatform platform,
            Text content
    ) {
        var textChatComponent = Text.translatable(
                "chat.type.mca.system", // platform, content
                Text.literal(platform.platformDisplayName()),
                content
        );

        var narrateChatComponent = Text.translatable(
                "chat.type.mca.system.narrate", // platform, content
                Text.literal(platform.platformDisplayName()),
                content
        );

        addMessage(textChatComponent, narrateChatComponent);
    }

    private static void addMessage(Text message, Text narrateMessage) {
        var minecraft = MinecraftClient.getInstance();
        minecraft.inGameHud.getChatHud().addMessage(message);
        minecraft.getNarratorManager().narrateChatMessage(() -> narrateMessage);
    }
}
