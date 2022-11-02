package info.skyblond.mc.mca.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class MixinChatScreen {

    @Accessor
    abstract EditBox getInput();

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        getInput().setMaxLength(1024);
    }
}
