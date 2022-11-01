package info.skyblond.mc.mca.mixin;

import info.skyblond.mc.mca.MinecraftChatAlternative;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {


    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String message, Component preview, CallbackInfo ci) {
        var minecraft = Minecraft.getInstance();
        MinecraftChatAlternative.LOGGER.info("Intercepted chat message: {}", message);
        var currentPlayer = Objects.requireNonNull(minecraft.player);
        // To apply the hover, click style, use bound.decorate(Component)
        minecraft.gui.getChat().addMessage(
                Component.literal("????!!!!"), null,
                GuiMessageTag.chatNotSecure());
        // send as system message
        minecraft.getChatListener().handleSystemMessage(Component.literal("[I2P] <player> example???"), false);
        var chatTypeBound = new ChatType.Bound(
                new ChatType(
                        ChatType.DEFAULT_CHAT_DECORATION,
                        ChatTypeDecoration.withSender("chat.type.text.narrate")
                ),
                Component.literal("player_name?")
                        .withStyle(Style.EMPTY
                                .withClickEvent(
                                        new ClickEvent(
                                                ClickEvent.Action.SUGGEST_COMMAND,
                                                "/i2p pm some_player "
                                        )
                                )
                                .withHoverEvent(
                                        new HoverEvent(
                                                HoverEvent.Action.SHOW_ENTITY,
                                                new HoverEvent.EntityTooltipInfo(
                                                        EntityType.PLAYER,
                                                        currentPlayer.getUUID(),
                                                        // TODO online mode profile card?
                                                        Component.literal("Player name")
                                                )
                                        )
                                )
                                .withInsertion("player_name?")
                        ),
                null
        );
        // send chat but as system
        // sender uuid is full zero. But the same style and code with user sent message
        minecraft.getChatListener().handleChatMessage(
                PlayerChatMessage.system(new ChatMessageContent("chat system")),
                chatTypeBound
        );
        // send chat as player, have sender uuid
        minecraft.getChatListener().handleChatMessage(
                PlayerChatMessage.unsigned(
                        MessageSigner.create(currentPlayer.getUUID()),
                        new ChatMessageContent("chat user(self)")),
                chatTypeBound
        );
        // cancel sending, but how to show it on screen?
//        ci.cancel();
    }
}
