package info.skyblond.mc.mca.mixin;

import info.skyblond.mc.mca.ClientModEntry;
import info.skyblond.mc.mca.helper.MinecraftHelper;
import info.skyblond.mc.mca.i2p.MCAContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public abstract class MixinPlayerListHud {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void onGetPlayerName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        var text = cir.getReturnValue();
        var realName = entry.getProfile().getName();

        if (realName.equals(MinecraftHelper.getCurrentUsername())) {
            cir.setReturnValue(Text.translatable("%s (%s)", text, Text.literal("self")));
            cir.cancel();
            return;
        }

        try {
            // potential error: getPeer() not initialized
            var peerSession = ClientModEntry.getPeer().dumpSessions().stream()
                    .filter(s -> !s.isClosed() && s.useContextSync(c -> realName.equals(c.getUsername())))
                    .findAny();

            if (peerSession.isPresent()) {
                var source = peerSession.get().useContextSync(MCAContext::getSessionSource);
                var direction = "";
                switch (source) {
                    case CLIENT -> direction = "out";
                    case SERVER -> direction = "in";
                }
                cir.setReturnValue(Text.translatable("%s (%s)", text, Text.literal(direction)));
                cir.cancel();
            }
        } catch (Throwable ignored) {

        }
    }
}
