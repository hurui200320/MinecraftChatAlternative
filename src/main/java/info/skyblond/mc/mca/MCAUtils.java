package info.skyblond.mc.mca;

import info.skyblond.mc.mca.i2p.I2PChatPeer;
import info.skyblond.mc.mca.model.MCAPlatform;
import net.i2p.I2PAppContext;
import net.i2p.data.Destination;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// TODO: I don't know why those translation keys are just not working.
//       They work first, then suddenly stop working.
public class MCAUtils {
    public static void addOutgoingMessageToChat(
            @NotNull MCAPlatform platform,
            @NotNull String username,
            @NotNull Text content
    ) {
        var textChatComponent = Text.translatable(
//                "chat.mca.outgoing", // platform, player, content
                "You whisper to [%s] <%s>: %s", // platform, player, content
                Text.literal(platform.platformDisplayName()),
                Text.literal(username),
                content
        ).fillStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        String.format("!msg %s %s ", platform.platformCodeName(), username)
                )));

        var narrateChatComponent = Text.translatable(
//                "chat.mca.outgoing.narrate", // player, platform, content
                "You whisper to %s on %s: %s", // player, platform, content
                Text.literal(username),
                Text.literal(platform.platformDisplayName()),
                content
        );

        addRawMessage(textChatComponent, narrateChatComponent);
    }

    public static void addIncomingMessageToChat(
            @NotNull MCAPlatform platform,
            @NotNull String username,
            @NotNull Text content
    ) {
        var textChatComponent = Text.translatable(
//                "chat.mca.incoming", // platform, player, content
                "[%s] <%s> whispers to you: %s", // platform, player, content
                Text.literal(platform.platformDisplayName()),
                Text.literal(username),
                content
        ).fillStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        String.format("!msg %s %s ", platform.platformCodeName(), username)
                )));

        var narrateChatComponent = Text.translatable(
//                "chat.mca.incoming.narrate", // player, platform, content
                "%s on %s whispers to you: %s", // player, platform, content
                Text.literal(username),
                Text.literal(platform.platformDisplayName()),
                content
        );

        addRawMessage(textChatComponent, narrateChatComponent);
    }

    public static void addBroadcastMessageToChat(
            @NotNull MCAPlatform platform,
            @NotNull String username,
            @NotNull Text content
    ) {
        var textChatComponent = Text.translatable(
//                "chat.mca.text", // platform, player, content
                "[%s] <%s> %s", // platform, player, content
                Text.literal(platform.platformDisplayName()),
                Text.literal(username),
                content
        ).fillStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND,
                        String.format("!msg %s %s ", platform.platformCodeName(), username)
                )));

        var narrateChatComponent = Text.translatable(
//                "chat.mca.text.narrate", // player, platform, content
                "%s on %s says %s", // player, platform, content
                Text.literal(username),
                Text.literal(platform.platformDisplayName()),
                content
        );

        addRawMessage(textChatComponent, narrateChatComponent);
    }

    public static void addSystemMessageToChat(
            @NotNull MCAPlatform platform,
            @NotNull Text content
    ) {
        var textChatComponent = Text.translatable(
//                "chat.mca.system", // platform, content
                "[%s] %s", // platform, content
                Text.literal(platform.platformDisplayName()),
                content
        );

        var narrateChatComponent = Text.translatable(
//                "chat.mca.system.narrate", // platform, content
                "%s: %s", // platform, content
                Text.literal(platform.platformDisplayName()),
                content
        );

        addRawMessage(textChatComponent, narrateChatComponent);
    }

    public static void addRawMessage(@NotNull Text message, @NotNull Text narrateMessage) {
        var minecraft = MinecraftClient.getInstance();
        if (minecraft.inGameHud != null) {
            minecraft.inGameHud.getChatHud().addMessage(message);
        }
        minecraft.getNarratorManager().narrateChatMessage(() -> narrateMessage);
    }

    public static @Nullable String getCurrentUsername() {
        var minecraft = MinecraftClient.getInstance();
        var session = minecraft.getSession();
        if (session != null) {
            return session.getUsername();
        }
        return null;
    }

    public static @NotNull List<String> getPlayerList() {
        var minecraft = MinecraftClient.getInstance();
        var networkHandler = minecraft.getNetworkHandler();
        if (networkHandler != null) {
            return networkHandler.getPlayerList().stream()
                    .map(p -> p.getProfile().getName()).toList();
        } else {
            if (getCurrentUsername() != null) {
                return List.of(getCurrentUsername());
            }
        }
        return List.of();
    }

    public static void runLater(@NotNull Runnable r) {
        var t = new Thread(r);
        t.setDaemon(true);
        t.start();
    }

    public static void sleepInaccurate(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {
        }
    }

    public static @Nullable Destination resolveB32Address(@NotNull String b32Address) {
        try {
            return I2PAppContext.getGlobalContext().namingService().lookup(b32Address);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Return null if ok, else failed reason
     */
    public static String tryConnectB32Address(I2PChatPeer peer, String username, String b32Address) {
        var dest = MCAUtils.resolveB32Address(b32Address);
        if (dest != null) {
            if (!peer.connect(username, dest)) {
                // failed
                return "Connection rejected by " + username;
            }
        } else {
            return "Bad b32 address";
        }
        return null;
    }
}
