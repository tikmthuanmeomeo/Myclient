package dev.luminous.mod.gui.items.buttons;

import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.gui.WeatherTheme;
import dev.luminous.mod.modules.impl.player.InvSaver;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** 9 x 3 grid showing the inventory saved by InvSaver. */
public class InvSlotsButton extends Button {
    private final InvSaver saver;

    public InvSlotsButton(InvSaver saver) {
        super("Inventory");
        this.saver = saver;
    }

    private float cell() {
        return (this.width + 7.0f) / 9.0f;
    }

    @Override
    public int getHeight() {
        return (int) (cell() * 3f);
    }

    @Override
    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        float cell = cell();
        float scale = Math.min(1f, (cell - 3f) / 16f);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                float cx = this.x + col * cell;
                float cy = this.y + row * cell;
                ItemStack stack = saver.getSavedStack(row * 9 + col);
                Render2DUtil.rect(context.getMatrices(), cx + 0.5f, cy, cx + cell - 0.5f, cy + cell - 1f,
                        stack.isEmpty() ? 0x55101A3A : 0x99123F80);
                WeatherTheme.outline(context.getMatrices(), cx + 0.5f, cy, cx + cell - 0.5f, cy + cell - 1f,
                        stack.isEmpty() ? 0x553A4F80 : WeatherTheme.BORDER);
                if (!stack.isEmpty()) {
                    context.getMatrices().push();
                    context.getMatrices().translate(cx + (cell - 16f * scale) / 2f, cy + (cell - 1f - 16f * scale) / 2f, 0f);
                    context.getMatrices().scale(scale, scale, 1f);
                    context.drawItem(stack, 0, 0);
                    context.getMatrices().pop();
                }
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    @Override
    public boolean getState() {
        return false;
    }
}
