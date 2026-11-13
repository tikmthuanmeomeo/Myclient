package dev.luminous.mod.gui.items.buttons;

import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.gui.BlockListScreen;
import dev.luminous.mod.gui.ClickGuiScreen;
import dev.luminous.mod.gui.WeatherTheme;
import dev.luminous.mod.modules.settings.impl.BlockListSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Row for a BlockListSetting: shows how many blocks are selected and opens the block picker. */
public class BlockListButton extends Button {
    private final BlockListSetting setting;

    public BlockListButton(BlockListSetting setting) {
        super(setting.getName());
        this.setting = setting;
    }

    @Override
    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        float x2 = this.x + (float) this.width + 7.0f;
        float y2 = this.y + (float) this.height - 0.5f;
        Render2DUtil.rect(context.getMatrices(), this.x, this.y, x2, y2, this.isHovering(mouseX, mouseY) ? hoverColor : defaultColor);
        float ty = this.y - 1.7f - (float) ClickGuiScreen.getInstance().getTextOffset();
        drawString(setting.getName() + " \u00a77[" + setting.size() + "]", this.x + 2.3f, ty, defaultTextColor);
        drawString("...", x2 - 9.0f, ty, WeatherTheme.DOTS);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.isHovering(mouseX, mouseY)) {
            sound();
            MinecraftClient.getInstance().setScreen(new BlockListScreen(setting));
        }
    }

    @Override
    public boolean getState() {
        return false;
    }
}
