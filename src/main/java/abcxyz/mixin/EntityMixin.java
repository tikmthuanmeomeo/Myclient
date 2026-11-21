package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.combat.Hitbox;
import abcxyz.module.render.Freecam;
import abcxyz.module.render.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
    /** The "targeting margin" is how much the box of an entity is expanded when finding what is under the crosshair. */
    @Inject(method = "getTargetingMargin", at = @At("RETURN"), cancellable = true)
    private void abcxyz$hitbox(CallbackInfoReturnable<Float> cir) {
        if (AbcxyzClient.modules == null) return;
        Hitbox hitbox = AbcxyzClient.modules.get(Hitbox.class);
        if (hitbox != null && hitbox.isEnabled() && hitbox.applies((Entity) (Object) this)) {
            cir.setReturnValue(cir.getReturnValue() + (float) hitbox.size.d());
        }
    }

    /** Freecam / FreeLook: the mouse turns the camera instead of the player. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void abcxyz$look(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (AbcxyzClient.modules == null || (Object) this != MinecraftClient.getInstance().player) return;
        Freecam freecam = AbcxyzClient.modules.get(Freecam.class);
        if (freecam != null && freecam.isActive()) {
            freecam.look(cursorDeltaX, cursorDeltaY);
            ci.cancel();
            return;
        }
        FreeLook freeLook = AbcxyzClient.modules.get(FreeLook.class);
        if (freeLook != null && freeLook.active()) {
            freeLook.look(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
