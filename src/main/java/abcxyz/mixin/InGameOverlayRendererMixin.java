package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.render.NoRender;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
    /** NoRender / In wall: the dark overlay shown when your head is inside a block (for example after pearling into a wall). */
    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void abcxyz$noInWall(CallbackInfo ci) {
        if (AbcxyzClient.modules == null) return;
        NoRender module = AbcxyzClient.modules.get(NoRender.class);
        if (module != null && module.noInWall()) ci.cancel();
    }
}
