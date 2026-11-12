package dev.luminous.mod.gui.items.buttons;

import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.gui.ClickGuiScreen;
import dev.luminous.mod.gui.WeatherTheme;
import dev.luminous.mod.modules.impl.player.HotbarSaver;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Row of 9 boxes (slots 1-9) showing the items saved by HotbarSaver. */
public class HotbarSlotsButton extends Button {
    private final HotbarSaver saver;

    public HotbarSlotsButton(HotbarSaver saver) {
        super("Hotbar");
        this.saver = saver;
    }

    private float cell() {
        return (this.width + 7.0f) / 9.0f;
    }

    @Override
    public int getHeight() {
        return (int) cell() + 11;
    }

    @Override
    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        float cell = cell();
        float scale = Math.min(1f, (cell - 3f) / 16f);
        for (int i = 0; i < 9; i++) {
            float cx = this.x + i * cell;
            ItemStack stack = saver.getSavedStack(i);
            Render2DUtil.rect(context.getMatrices(), cx + 0.5f, this.y, cx + cell - 0.5f, this.y + cell - 1f,
                    stack.isEmpty() ? 0x55101A3A : 0x99123F80);
            WeatherTheme.outline(context.getMatrices(), cx + 0.5f, this.y, cx + cell - 0.5f, this.y + cell - 1f,
                    stack.isEmpty() ? 0x553A4F80 : WeatherTheme.BORDER);
            if (!stack.isEmpty()) {
                context.getMatrices().push();
                context.getMatrices().translate(cx + (cell - 16f * scale) / 2f, this.y + (cell - 1f - 16f * scale) / 2f, 0f);
                context.getMatrices().scale(scale, scale, 1f);
                context.drawItem(stack, 0, 0);
                context.getMatrices().pop();
            }
            drawString(String.valueOf(i + 1), cx + cell / 2f - 2f, this.y + cell - 2.2f - (float) ClickGuiScreen.getInstance().getTextOffset(),
                    stack.isEmpty() ? defaultTextColor : enableTextColor);
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
