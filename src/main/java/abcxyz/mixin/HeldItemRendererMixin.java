package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.render.FovChanger;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    /** Start of drawing one first-person hand/item: FovChanger moves it back when the FOV is wide. */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void abcxyz$fovHand(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (AbcxyzClient.modules == null) return;
        FovChanger fovChanger = AbcxyzClient.modules.get(FovChanger.class);
        if (fovChanger != null && fovChanger.isEnabled()) fovChanger.transformHand(matrices);
    }
}
