package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.render.NoRender;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    /** NoRender / Items: dropped items are simply never drawn. */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void abcxyz$noItems(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof ItemEntity) || AbcxyzClient.modules == null) return;
        NoRender module = AbcxyzClient.modules.get(NoRender.class);
        if (module != null && module.noItems()) cir.setReturnValue(false);
    }
}
