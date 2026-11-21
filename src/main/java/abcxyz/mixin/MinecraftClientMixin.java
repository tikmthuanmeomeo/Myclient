package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.misc.AutoShop;
import abcxyz.module.misc.FastUse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    /**
     * AutoShop: don't show the shop window. When the server opens a chest-like window the client has already made
     * it the current screen handler; only putting it on screen is skipped, so clicks and updates still work.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void abcxyz$hideShop(Screen screen, CallbackInfo ci) {
        if (!(screen instanceof GenericContainerScreen) || AbcxyzClient.modules == null) return;
        AutoShop shop = AbcxyzClient.modules.get(AutoShop.class);
        if (shop != null && shop.hidesGui()) ci.cancel();
    }

    @Shadow
    private int itemUseCooldown;

    /** FastUse: the vanilla wait of 4 ticks between two right clicks is cut down. */
    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void abcxyz$fastUse(CallbackInfo ci) {
        if (AbcxyzClient.modules == null) return;
        FastUse fastUse = AbcxyzClient.modules.get(FastUse.class);
        if (fastUse != null && fastUse.isEnabled() && fastUse.applies() && itemUseCooldown > fastUse.cooldown()) {
            itemUseCooldown = fastUse.cooldown();
        }
    }
}
