package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.render.Freecam;
import abcxyz.module.render.FreeLook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean thirdPerson;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    /** Freecam: put the camera where the free camera is. FreeLook: only turn it. Done after vanilla has placed it. */
    @Inject(method = "update", at = @At("TAIL"))
    private void abcxyz$update(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (AbcxyzClient.modules == null) return;
        Freecam freecam = AbcxyzClient.modules.get(Freecam.class);
        if (freecam != null && freecam.isActive()) {
            setRotation(freecam.getYaw(tickDelta), freecam.getPitch(tickDelta));
            setPos(freecam.getX(tickDelta), freecam.getY(tickDelta), freecam.getZ(tickDelta));
            this.thirdPerson = true; // so your own body is drawn
            return;
        }
        FreeLook freeLook = AbcxyzClient.modules.get(FreeLook.class);
        if (freeLook != null && freeLook.active()) setRotation(freeLook.yaw(), freeLook.pitch());
    }
}
