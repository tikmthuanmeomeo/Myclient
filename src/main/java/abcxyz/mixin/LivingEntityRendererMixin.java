package abcxyz.mixin;

import abcxyz.core.Rotations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /**
     * Silent rotation (SafeAnchor): the camera stays, but your own model (seen in F5 or Freecam) turns its head to where
     * the server was told you look. Only the drawing is changed. (require = 0: never stops the game from starting.)
     */
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"), require = 0)
    private void abcxyz$silentHead(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (entity != MinecraftClient.getInstance().player || !Rotations.hasVisual()) return;
        float rel = MathHelper.wrapDegrees(Rotations.visualYaw() - state.bodyYaw);
        if (Math.abs(rel) > 75.0f) {
            // the head only turns 75 degrees from the body, past that the body turns too
            float limit = Math.signum(rel) * 75.0f;
            state.bodyYaw += rel - limit;
            rel = limit;
        }
        state.yawDegrees = rel;
        state.pitch = Rotations.visualPitch();
    }
}
