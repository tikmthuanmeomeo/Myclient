package dev.luminous.mod.gui.items.buttons;

import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.gui.ClickGuiScreen;
import dev.luminous.mod.gui.WeatherTheme;
import net.minecraft.client.gui.DrawContext;

/** Collapsible header row that groups settings (Place, Break, Render ...). */
public class SectionButton extends Button {
    public boolean open;

    public SectionButton(String name, boolean open) {
        super(name);
        this.open = open;
    }

    @Override
    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        float x2 = this.x + (float) this.width + 7.0f;
        float y2 = this.y + (float) this.height - 0.5f;
        Render2DUtil.horizontalGradient(context.getMatrices(), this.x, this.y, x2, y2,
                this.isHovering(mouseX, mouseY) ? WeatherTheme.ROW_ON_LEFT_HOVER : WeatherTheme.ROW_ON_LEFT, WeatherTheme.ROW_ON_RIGHT);
        Render2DUtil.rect(context.getMatrices(), this.x, this.y, this.x + 2.0f, y2, WeatherTheme.ACCENT);
        drawString(this.getName(), this.x + 5.0f, this.y - 1.7f - (float) ClickGuiScreen.getInstance().getTextOffset(), enableTextColor);
        drawString(open ? "-" : "+", x2 - 8.0f, this.y - 1.7f - (float) ClickGuiScreen.getInstance().getTextOffset(), WeatherTheme.DOTS);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if ((mouseButton == 0 || mouseButton == 1) && this.isHovering(mouseX, mouseY)) {
            this.open = !this.open;
            sound();
        }
    }

    @Override
    public boolean getState() {
        return this.open;
    }
}
