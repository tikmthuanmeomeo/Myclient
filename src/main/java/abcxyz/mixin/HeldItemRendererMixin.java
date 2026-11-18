package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.render.HandView;
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
    /** Start of drawing one first-person hand/item: apply the HandView position, rotation and scale. */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void abcxyz$handView(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                 float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (AbcxyzClient.modules == null) return;
        HandView handView = AbcxyzClient.modules.get(HandView.class);
        if (handView != null && handView.isEnabled()) handView.transform(hand, matrices);
    }
}
