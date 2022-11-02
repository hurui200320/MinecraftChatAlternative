package info.skyblond.mc.mca.mixin;

import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;

import static info.skyblond.mc.mca.MinecraftChatAlternative.LOGGER;
import static info.skyblond.mc.mca.MinecraftChatAlternative.PEER;

@Mixin(ChatListener.class)
public class MixinChatListener {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "handleChatMessage", at = @At("HEAD"), cancellable = true)
    private void onHandleChatMessage(
            PlayerChatMessage playerChatMessage, ChatType.Bound bound, CallbackInfo ci
    ) {
        var textContent = playerChatMessage.signedBody().content().plain();
        LOGGER.info("Intercepted in-game incoming chat: {}", textContent);

    }

    @Inject(method = "showMessageToPlayer", at = @At("HEAD"))
    private void onShowMessageToPlayer(ChatType.Bound bound, PlayerChatMessage playerChatMessage, Component component, PlayerInfo playerInfo, boolean bl, Instant instant, CallbackInfoReturnable<Boolean> cir) {
        LOGGER.info("Show chat: {}", component);
    }
}
