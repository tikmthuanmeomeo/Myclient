package abcxyz.gui;

import abcxyz.AbcxyzClient;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Config;
import abcxyz.core.Module;
import abcxyz.core.ModeSetting;
import abcxyz.core.NumSetting;
import abcxyz.core.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic click GUI: one column per category.
 * Left click toggles a module, right click shows its settings, middle click sets its keybind.
 */
public class ClickGuiScreen extends Screen {
    private static final int PANEL_W = 112;
    private static final int HEADER_H = 14;
    private static final int ROW_H = 13;
    private static final int SETTING_H = 12;
    private static final int ACCENT = 0xFF4AA3FF;

    private enum Kind {MODULE, BOOL, NUM, MODE, BIND}

    private record Hit(int x1, int y1, int x2, int y2, Kind kind, Module module, Setting<?> setting) {
        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private Module listeningBind = null;
    private Hit dragging = null;

    public ClickGuiScreen() {
        super(Text.literal("abcxyz"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static String keyName(int key) {
        if (key < 0) return "None";
        return InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x55000000);
        hits.clear();
        if (AbcxyzClient.modules == null) return;
        int x = 8;
        for (Category category : Category.values()) {
            int y = 8;
            context.fill(x, y, x + PANEL_W, y + HEADER_H, 0xFF101010);
            context.fill(x, y + HEADER_H - 1, x + PANEL_W, y + HEADER_H, ACCENT);
            context.drawTextWithShadow(textRenderer, category.name(), x + 4, y + 3, 0xFFFFFFFF);
            y += HEADER_H;
            for (Module m : AbcxyzClient.modules.byCategory(category)) {
                boolean hover = mouseX >= x && mouseX < x + PANEL_W && mouseY >= y && mouseY < y + ROW_H;
                int bg = m.isEnabled() ? (hover ? 0xFF3A7FCC : 0xFF2F6AB0) : (hover ? 0xFF2C2C2C : 0xFF1E1E1E);
                context.fill(x, y, x + PANEL_W, y + ROW_H, bg);
                context.drawTextWithShadow(textRenderer, m.name, x + 4, y + 3, m.isEnabled() ? 0xFFFFFFFF : 0xFFBBBBBB);
                if (!m.settings.isEmpty()) {
                    context.drawTextWithShadow(textRenderer, m.expanded ? "-" : "+", x + PANEL_W - 10, y + 3, 0xFF999999);
                }
                hits.add(new Hit(x, y, x + PANEL_W, y + ROW_H, Kind.MODULE, m, null));
                y += ROW_H;
                if (m.expanded) {
                    for (Setting<?> s : m.settings) {
                        y = drawSetting(context, x, y, m, s);
                    }
                    String bind = listeningBind == m ? "press a key..." : keyName(m.key);
                    context.fill(x, y, x + PANEL_W, y + SETTING_H, 0xFF161616);
                    context.drawTextWithShadow(textRenderer, "Bind: " + bind, x + 8, y + 2, 0xFFCCCCCC);
                    hits.add(new Hit(x, y, x + PANEL_W, y + SETTING_H, Kind.BIND, m, null));
                    y += SETTING_H;
                }
            }
            x += PANEL_W + 6;
        }
        String hint = "Left: toggle   Right: settings   Middle: keybind   Right Shift: close";
        context.drawTextWithShadow(textRenderer, hint, 8, height - 12, 0xFF888888);
    }

    private int drawSetting(DrawContext context, int x, int y, Module m, Setting<?> s) {
        context.fill(x, y, x + PANEL_W, y + SETTING_H, 0xFF161616);
        if (s instanceof BoolSetting b) {
            context.drawTextWithShadow(textRenderer, b.name, x + 8, y + 2, 0xFFCCCCCC);
            context.drawTextWithShadow(textRenderer, b.on() ? "ON" : "OFF", x + PANEL_W - 24, y + 2, b.on() ? 0xFF55FF55 : 0xFFFF5555);
            hits.add(new Hit(x, y, x + PANEL_W, y + SETTING_H, Kind.BOOL, m, s));
        } else if (s instanceof NumSetting n) {
            double ratio = (n.d() - n.min) / (n.max - n.min);
            context.fill(x, y, x + (int) (PANEL_W * ratio), y + SETTING_H, 0xFF24527F);
            String value = n.step >= 1 ? String.valueOf(n.i()) : String.format("%.2f", n.d());
            context.drawTextWithShadow(textRenderer, n.name + ": " + value, x + 8, y + 2, 0xFFCCCCCC);
            hits.add(new Hit(x, y, x + PANEL_W, y + SETTING_H, Kind.NUM, m, s));
        } else if (s instanceof ModeSetting mode) {
            context.drawTextWithShadow(textRenderer, mode.name + ": " + mode.get(), x + 8, y + 2, 0xFFCCCCCC);
            hits.add(new Hit(x, y, x + PANEL_W, y + SETTING_H, Kind.MODE, m, s));
        }
        return y + SETTING_H;
    }

    private void applySlider(Hit hit, double mouseX) {
        NumSetting n = (NumSetting) hit.setting();
        double ratio = (mouseX - hit.x1()) / (double) (hit.x2() - hit.x1());
        ratio = Math.max(0, Math.min(1, ratio));
        n.set(n.min + ratio * (n.max - n.min));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit hit : hits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            switch (hit.kind()) {
                case MODULE -> {
                    if (button == 0) hit.module().toggle();
                    else if (button == 1) hit.module().expanded = !hit.module().expanded;
                    else if (button == 2) listeningBind = hit.module();
                }
                case BOOL -> {
                    if (button == 0) {
                        ((BoolSetting) hit.setting()).toggle();
                        Config.save();
                    }
                }
                case MODE -> {
                    if (button == 0) {
                        ((ModeSetting) hit.setting()).cycle();
                        Config.save();
                    }
                }
                case NUM -> {
                    if (button == 0) {
                        dragging = hit;
                        applySlider(hit, mouseX);
                    }
                }
                case BIND -> listeningBind = hit.module();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != null) {
            applySlider(dragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            Config.save();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningBind != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                listeningBind.key = -1;
            } else {
                listeningBind.key = keyCode;
            }
            listeningBind = null;
            Config.save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        Config.save();
    }
}
