package dev.luminous.mod.gui.windows;

import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.gui.ClickGuiScreen;
import dev.luminous.mod.modules.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.luminous.api.utils.Wrapper.mc;

public class WindowsScreen extends Screen {
    public static WindowBase lastClickedWindow;
    public static WindowBase draggingWindow;
    private List<WindowBase> windows = new ArrayList<>();

    public WindowsScreen(WindowBase... windows) {
        super(Text.of("CustomWindows"));
        this.windows.clear();
        lastClickedWindow = null;
        this.windows = Arrays.stream(windows).toList();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (Module.nullCheck())
            renderBackground(context, mouseX, mouseY, delta);

        windows.stream().filter(WindowBase::isVisible).forEach(w -> {
            if (w != lastClickedWindow)
                w.render(context, mouseX, mouseY);
        });

        if (lastClickedWindow != null && lastClickedWindow.isVisible())
            lastClickedWindow.render(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        windows.forEach(w -> w.mouseReleased(mouseX, mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        windows.stream().filter(WindowBase::isVisible).forEach(w -> w.mouseClicked(mouseX, mouseY, button));

        int i = mc.getWindow().getScaledWidth() / 2;
        float offset = (windows.size() * 20f) / -2f - 23;

        if (Render2DUtil.isHovered(mouseX, mouseY, (i + offset) + 1, mc.getWindow().getScaledHeight() - 23, 15, 15))
            mc.setScreen(ClickGuiScreen.getInstance());

        offset += 23;
        for (WindowBase w : windows) {
            if (Render2DUtil.isHovered(mouseX, mouseY, i + offset, mc.getWindow().getScaledHeight() - 24, 17, 17))
                w.setVisible(!w.isVisible());
            offset += 20f;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        windows.stream().filter(WindowBase::isVisible).forEach(w -> w.keyPressed(keyCode, scanCode, modifiers));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char key, int keyCode) {
        windows.stream().filter(WindowBase::isVisible).forEach(w -> w.charTyped(key, keyCode));
        return super.charTyped(key, keyCode);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        windows.stream().filter(WindowBase::isVisible).forEach(w -> w.mouseScrolled((int) (verticalAmount * 5D)));
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
