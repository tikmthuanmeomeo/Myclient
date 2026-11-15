package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.combat.Hitbox;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}
