package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.render.NoRender;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
    /** NoRender / Fog: push the start and end of the terrain fog far past the view distance. (require = 0: never stops the game from starting.) */
    @ModifyArgs(method = "applyFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Fog;<init>(FFLnet/minecraft/client/render/FogShape;FFFF)V"), require = 0)
    private static void abcxyz$noFog(Args args, Camera camera, BackgroundRenderer.FogType fogType, Vector4f color, float viewDistance, boolean thickenFog, float tickDelta) {
        if (AbcxyzClient.modules == null || fogType != BackgroundRenderer.FogType.FOG_TERRAIN) return;
        NoRender module = AbcxyzClient.modules.get(NoRender.class);
        if (module != null && module.noFog()) {
            args.set(0, viewDistance * 4.0f);
            args.set(1, viewDistance * 4.25f);
        }
    }
}
