package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.render.HandView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    /** How many ticks a swing lasts. Only for your own player, and only the animation: the server is not affected. */
    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    private void abcxyz$swingSpeed(CallbackInfoReturnable<Integer> cir) {
        if (AbcxyzClient.modules == null) return;
        if ((Object) this != MinecraftClient.getInstance().player) return;
        HandView handView = AbcxyzClient.modules.get(HandView.class);
        if (handView != null && handView.isEnabled()) cir.setReturnValue(handView.swingDuration());
    }
}
