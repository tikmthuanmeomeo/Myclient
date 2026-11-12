package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.misc.ShulkerViewer;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DrawContext.class)
public class MixinDrawContext {

    @Unique
    private static final ItemStack[] ITEMS = new ItemStack[27];

    @Inject(method = "drawItemInSlot(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    public void onDrawItem(TextRenderer textRenderer, ItemStack stack, int x, int y, String countOverride, CallbackInfo ci) {
        if (ShulkerViewer.INSTANCE.isOn() && ShulkerViewer.INSTANCE.icon.getValue() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            ShulkerViewer.getItemsInContainerItem(stack, ITEMS);
            for (ItemStack itemStack : ITEMS) {
                if (!itemStack.isEmpty()) {
                    matrices.push();
                    matrices.scale(0.5f, 0.5f, 1);
                    drawItem(itemStack, x * 2 + 20, y * 2 + 20);
                    matrices.pop();
                    return;
                }
            }
        }
    }

    @Final
    @Shadow
    private MatrixStack matrices;

    @Shadow
    public void drawItem(ItemStack item, int x, int y) {
    }
}
