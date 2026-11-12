package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.mod.modules.impl.misc.ShulkerViewer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.luminous.api.utils.Wrapper.mc;

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen<T extends ScreenHandler> extends Screen implements ScreenHandlerProvider<T> {

    @Unique
    private static final ItemStack[] ITEMS = new ItemStack[27];
    @Shadow
    @Nullable
    protected Slot focusedSlot;
    @Shadow
    protected int x;
    @Shadow
    protected int y;

    protected MixinHandledScreen(Text title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && focusedSlot != null && !focusedSlot.getStack().isEmpty() && mc.player.currentScreenHandler.getCursorStack().isEmpty() && ShulkerViewer.INSTANCE.isOn()) {
            ItemStack itemStack = focusedSlot.getStack();
            if (ShulkerViewer.hasItems(itemStack) || itemStack.getItem() == Items.ENDER_CHEST && Alien.PLAYER.known) {
                cir.setReturnValue(ShulkerViewer.openContainer(focusedSlot.getStack(), ITEMS, false));
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ShulkerViewer.INSTANCE.isOn() && ShulkerViewer.INSTANCE.toolTips.getValue()) {
            if (focusedSlot != null && !focusedSlot.getStack().isEmpty() && client.player.playerScreenHandler.getCursorStack().isEmpty()) {
                if (ShulkerViewer.hasItems(focusedSlot.getStack()) || focusedSlot.getStack().getItem() == Items.ENDER_CHEST && Alien.PLAYER.known) {
                    ShulkerViewer.renderShulkerToolTip(context, mouseX, mouseY, focusedSlot.getStack());
                }
            }
        }
    }

    @Shadow
    public abstract void renderBackground(DrawContext context, int mouseX, int mouseY, float delta);
}
