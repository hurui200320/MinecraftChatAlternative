package info.skyblond.mc.mca.mixin;

import info.skyblond.mc.mca.MinecraftChatAlternative;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatListener.class)
public class MixinChatListener {
    @Inject(method = "handleChatMessage", at = @At("HEAD"), cancellable = true)
    private void onHandleChatMessage(
            PlayerChatMessage playerChatMessage, ChatType.Bound bound, CallbackInfo ci
    ) {
        MinecraftChatAlternative.LOGGER.info("handle chat message:\n\tmessage: {}\n\tbound: {}", playerChatMessage, bound);
        var event = bound.name().getStyle().getHoverEvent();
        if (event != null) {
            var entity = (HoverEvent.EntityTooltipInfo) event.getValue(event.getAction());
            if (entity != null) {
                MinecraftChatAlternative.LOGGER.info("HOVER VALUE CLASS: {}, {}, {}",
                        entity.id, entity.name, entity.type);
            }
        }
    }
}
