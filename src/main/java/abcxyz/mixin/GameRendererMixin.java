package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.render.NoRender;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    /** This is the method that tilts/shakes the camera while the player's hurt animation is running. */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void abcxyz$noHurtCam(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (AbcxyzClient.modules == null) return;
        NoRender module = AbcxyzClient.modules.get(NoRender.class);
        if (module != null && module.noHurtCam()) ci.cancel();
    }
}
