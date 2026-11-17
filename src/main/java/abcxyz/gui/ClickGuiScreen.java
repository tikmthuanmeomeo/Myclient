package abcxyz.gui;

import abcxyz.AbcxyzClient;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Config;
import abcxyz.core.KeySetting;
import abcxyz.core.Module;
import abcxyz.core.ModeSetting;
import abcxyz.core.NumSetting;
import abcxyz.core.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Click GUI: one draggable panel per category.
 * Left click toggles a module, right click opens its settings, middle click sets its keybind.
 * Drag a header to move a panel, right click a header to collapse it, scroll over a panel to scroll it.
 */
public class ClickGuiScreen extends Screen {
    private static final int PANEL_W = 122;
    private static final int HEADER_H = 20;
    private static final int ROW_H = 17;
    private static final int SETTING_H = 15;

    private static final int ACCENT_A = 0xFF8B5CFF;
    private static final int ACCENT_B = 0xFF33B5FF;
    private static final int PANEL_BG = 0xF0121218;
    private static final int HEADER_BG = 0xFF1B1B26;
    private static final int ROW_BG = 0xFF181820;
    private static final int ROW_HOVER = 0xFF23232F;
    private static final int SETTING_BG = 0xFF14141B;
    private static final int CHILD_BG = 0xFF191923;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int TEXT_DIM = 0xFF9A9AAE;

    /** Panel positions {x, y}; saved in the config. */
    public static final Map<Category, int[]> POSITIONS = new EnumMap<>(Category.class);
    private static final Set<Category> COLLAPSED = EnumSet.noneOf(Category.class);

    private enum Kind {HEADER, MODULE, BOOL, NUM, MODE, KEY, BIND}

    private record Hit(int x1, int y1, int x2, int y2, Kind kind, Module module, Setting<?> setting, Category category) {
        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Category, Float> scroll = new EnumMap<>(Category.class);
    private final Map<Category, Integer> bodyHeights = new EnumMap<>(Category.class);
    private final Map<Module, Float> expandAnim = new IdentityHashMap<>();
    private final Map<Module, Float> enableAnim = new IdentityHashMap<>();
    private final Map<Module, Float> hoverAnim = new IdentityHashMap<>();
    private final Map<Setting<?>, Float> boolAnim = new IdentityHashMap<>();

    private Module listeningBind = null;
    private KeySetting listeningKey = null;
    private Hit dragging = null;
    private Category dragPanel = null;
    private int dragOffX, dragOffY;
    private long lastNs = 0L;

    public ClickGuiScreen() {
        super(Text.literal("abcxyz"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private static String keyName(int key) {
        if (key < 0) return "None";
        return InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 24) & 255, rr = (a >> 16) & 255, gr = (a >> 8) & 255, br = a & 255;
        int a2 = (b >> 24) & 255, r2 = (b >> 16) & 255, g2 = (b >> 8) & 255, b2 = b & 255;
        return ((int) (ar + (a2 - ar) * t) << 24) | ((int) (rr + (r2 - rr) * t) << 16)
                | ((int) (gr + (g2 - gr) * t) << 8) | (int) (br + (b2 - br) * t);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    private static <K> float step(Map<K, Float> map, K key, float target, float dt) {
        float v = map.getOrDefault(key, target);
        v += (target - v) * Math.min(1f, dt * 16f);
        if (Math.abs(target - v) < 0.004f) v = target;
        map.put(key, v);
        return v;
    }

    /** A rectangle with slightly cut corners. */
    private static void rect(DrawContext c, int x1, int y1, int x2, int y2, int color) {
        c.fill(x1 + 2, y1, x2 - 2, y2, color);
        c.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, color);
        c.fill(x1, y1 + 2, x2, y2 - 2, color);
    }

    private int[] pos(Category category) {
        return POSITIONS.computeIfAbsent(category, c -> new int[]{14 + c.ordinal() * (PANEL_W + 10), 14});
    }

    private int extraHeight(Module m) {
        int rows = 1; // bind row
        for (Setting<?> s : m.settings) {
            if (s.parent != null) continue;
            rows++;
            if (s instanceof BoolSetting b && b.open) rows += b.children.size();
        }
        return rows * SETTING_H;
    }

    private void addHit(int x1, int y1, int x2, int y2, int clipTop, int clipBottom,
                        Kind kind, Module m, Setting<?> s, Category c) {
        int a = Math.max(y1, clipTop), b = Math.min(y2, clipBottom);
        if (a >= b) return;
        hits.add(new Hit(x1, a, x2, b, kind, m, s, c));
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = lastNs == 0L ? 0.016f : Math.min(0.1f, (now - lastNs) / 1.0e9f);
        lastNs = now;

        context.fillGradient(0, 0, width, height, 0xB0080810, 0x80141436);
        hits.clear();
        if (AbcxyzClient.modules == null) return;

        for (Category category : Category.values()) drawPanel(context, category, mouseX, mouseY, dt);
        drawTooltip(context, mouseX, mouseY);

        String hint = "LMB toggle  |  RMB settings  |  MMB bind  |  drag header to move  |  RMB header collapse  |  Right Shift close";
        context.drawTextWithShadow(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, height - 12, 0xFF6E6E86);
    }

    private void drawPanel(DrawContext ctx, Category category, int mx, int my, float dt) {
        int[] p = pos(category);
        int x = p[0], y = p[1];
        boolean collapsed = COLLAPSED.contains(category);
        List<Module> modules = AbcxyzClient.modules.byCategory(category);

        int total = 0;
        for (Module m : modules) {
            float a = step(expandAnim, m, m.expanded ? 1f : 0f, dt);
            total += ROW_H + (int) (extraHeight(m) * a);
        }
        int top = y + HEADER_H;
        int maxBody = Math.max(60, height - top - 26);
        int bodyH = collapsed ? 0 : Math.min(total, maxBody);
        bodyHeights.put(category, bodyH);
        float maxScroll = Math.max(0, total - bodyH);
        float sc = Math.max(0f, Math.min(maxScroll, scroll.getOrDefault(category, 0f)));
        scroll.put(category, sc);

        // frame, header
        rect(ctx, x - 1, y - 1, x + PANEL_W + 1, top + bodyH + 1, 0x50000000);
        rect(ctx, x, y, x + PANEL_W, top + bodyH, PANEL_BG);
        rect(ctx, x, y, x + PANEL_W, top, HEADER_BG);
        ctx.fill(x, top - 2, x + PANEL_W, top, HEADER_BG);
        for (int i = 0; i < PANEL_W; i += 2) {
            ctx.fill(x + i, top - 2, x + Math.min(PANEL_W, i + 2), top - 1,
                    lerpColor(ACCENT_A, ACCENT_B, i / (float) PANEL_W));
        }
        ctx.drawTextWithShadow(textRenderer, Text.literal(category.name()).formatted(Formatting.BOLD), x + 8, y + 6, TEXT);
        int enabled = 0;
        for (Module m : modules) if (m.isEnabled()) enabled++;
        String count = enabled + "/" + modules.size();
        ctx.drawTextWithShadow(textRenderer, count, x + PANEL_W - 8 - textRenderer.getWidth(count), y + 6, TEXT_DIM);
        hits.add(new Hit(x, y, x + PANEL_W, top, Kind.HEADER, null, null, category));
        if (bodyH <= 0) return;

        ctx.enableScissor(x, top, x + PANEL_W, top + bodyH);
        int cy = top - (int) sc;
        int clipBottom = top + bodyH;
        for (Module m : modules) {
            int rowTop = cy;
            boolean inside = my >= top && my < clipBottom;
            boolean hover = inside && mx >= x && mx < x + PANEL_W && my >= rowTop && my < rowTop + ROW_H;
            float en = step(enableAnim, m, m.isEnabled() ? 1f : 0f, dt);
            float hv = step(hoverAnim, m, hover ? 1f : 0f, dt);

            ctx.fill(x, rowTop, x + PANEL_W, rowTop + ROW_H, lerpColor(ROW_BG, ROW_HOVER, hv));
            if (en > 0.01f) {
                ctx.fillGradient(x, rowTop, x + PANEL_W, rowTop + ROW_H,
                        withAlpha(ACCENT_A, (int) (150 * en)), withAlpha(ACCENT_B, (int) (110 * en)));
                ctx.fill(x, rowTop, x + 2, rowTop + ROW_H, withAlpha(ACCENT_B, (int) (255 * en)));
            }
            int textColor = lerpColor(TEXT_DIM, 0xFFFFFFFF, Math.max(en, hv * 0.6f));
            ctx.drawTextWithShadow(textRenderer, m.name, x + 8, rowTop + 5, textColor);
            if (!m.settings.isEmpty()) {
                String mark = m.expanded ? "-" : "+";
                ctx.drawTextWithShadow(textRenderer, mark, x + PANEL_W - 6 - textRenderer.getWidth(mark), rowTop + 5, TEXT_DIM);
            }
            addHit(x, rowTop, x + PANEL_W, rowTop + ROW_H, top, clipBottom, Kind.MODULE, m, null, category);
            cy += ROW_H;

            float ex = expandAnim.getOrDefault(m, 0f);
            if (ex > 0.004f) {
                int full = extraHeight(m);
                int drawn = (int) (full * ex);
                boolean live = m.expanded && ex >= 0.98f;
                ctx.enableScissor(x, cy, x + PANEL_W, cy + drawn);
                int sy = cy;
                for (Setting<?> s : m.settings) {
                    if (s.parent != null) continue;
                    sy = drawSetting(ctx, x, sy, m, s, 0, mx, my, dt, live, top, clipBottom, category);
                    if (s instanceof BoolSetting b && b.open) {
                        for (Setting<?> child : b.children) {
                            sy = drawSetting(ctx, x, sy, m, child, 8, mx, my, dt, live, top, clipBottom, category);
                        }
                    }
                }
                ctx.fill(x, sy, x + PANEL_W, sy + SETTING_H, SETTING_BG);
                String bind = listeningBind == m ? "..." : keyName(m.key);
                ctx.drawTextWithShadow(textRenderer, "Bind", x + 10, sy + 4, TEXT_DIM);
                ctx.drawTextWithShadow(textRenderer, bind, x + PANEL_W - 8 - textRenderer.getWidth(bind), sy + 4,
                        listeningBind == m ? ACCENT_B : TEXT);
                if (live) addHit(x, sy, x + PANEL_W, sy + SETTING_H, top, clipBottom, Kind.BIND, m, null, category);
                ctx.disableScissor();
                cy += drawn;
            }
        }
        ctx.disableScissor();

        // scroll bar
        if (maxScroll > 0) {
            int trackH = bodyH - 4;
            int thumbH = Math.max(12, (int) (trackH * (bodyH / (float) total)));
            int thumbY = top + 2 + (int) ((trackH - thumbH) * (sc / maxScroll));
            ctx.fill(x + PANEL_W - 3, thumbY, x + PANEL_W - 1, thumbY + thumbH, 0x99FFFFFF);
        }
    }

    private int drawSetting(DrawContext ctx, int x, int y, Module m, Setting<?> s, int indent, int mx, int my, float dt,
                            boolean live, int clipTop, int clipBottom, Category category) {
        int px = x + indent;
        int w = PANEL_W - indent;
        ctx.fill(x, y, x + PANEL_W, y + SETTING_H, SETTING_BG);
        if (indent > 0) {
            ctx.fill(px, y, x + PANEL_W, y + SETTING_H, CHILD_BG);
            ctx.fill(px, y, px + 1, y + SETTING_H, withAlpha(ACCENT_A, 160));
        }
        if (s instanceof BoolSetting b) {
            float t = step(boolAnim, b, b.on() ? 1f : 0f, dt);
            ctx.drawTextWithShadow(textRenderer, b.label, px + 9, y + 4, TEXT);
            int pillX = px + w - 8 - 16;
            int pillY = y + (SETTING_H - 8) / 2;
            rect(ctx, pillX, pillY, pillX + 16, pillY + 8, lerpColor(0xFF34343F, ACCENT_A, t));
            int knobX = pillX + 1 + (int) (8 * t);
            ctx.fill(knobX, pillY + 1, knobX + 6, pillY + 7, 0xFFFFFFFF);
            if (!b.children.isEmpty()) {
                String mark = b.open ? "-" : "+";
                ctx.drawTextWithShadow(textRenderer, mark, pillX - 4 - textRenderer.getWidth(mark), y + 4, TEXT_DIM);
            }
            if (live) addHit(px, y, px + w, y + SETTING_H, clipTop, clipBottom, Kind.BOOL, m, s, category);
        } else if (s instanceof NumSetting n) {
            double ratio = (n.d() - n.min) / (n.max - n.min);
            String value = n.step >= 1 ? String.valueOf(n.i()) : String.format("%.2f", n.d());
            ctx.drawTextWithShadow(textRenderer, n.label, px + 9, y + 2, TEXT);
            ctx.drawTextWithShadow(textRenderer, value, px + w - 8 - textRenderer.getWidth(value), y + 2, TEXT_DIM);
            int tx1 = px + 8, tx2 = px + w - 8, ty = y + SETTING_H - 4;
            ctx.fill(tx1, ty, tx2, ty + 2, 0xFF2C2C38);
            int fillW = (int) ((tx2 - tx1) * ratio);
            for (int i = 0; i < fillW; i += 2) {
                ctx.fill(tx1 + i, ty, tx1 + Math.min(fillW, i + 2), ty + 2,
                        lerpColor(ACCENT_A, ACCENT_B, i / (float) Math.max(1, tx2 - tx1)));
            }
            ctx.fill(tx1 + fillW - 1, ty - 1, tx1 + fillW + 1, ty + 3, 0xFFFFFFFF);
            if (live) addHit(px, y, px + w, y + SETTING_H, clipTop, clipBottom, Kind.NUM, m, s, category);
        } else if (s instanceof ModeSetting mode) {
            ctx.drawTextWithShadow(textRenderer, mode.label, px + 9, y + 4, TEXT);
            String value = mode.get();
            ctx.drawTextWithShadow(textRenderer, value, px + w - 8 - textRenderer.getWidth(value), y + 4, ACCENT_B);
            if (live) addHit(px, y, px + w, y + SETTING_H, clipTop, clipBottom, Kind.MODE, m, s, category);
        } else if (s instanceof KeySetting k) {
            ctx.drawTextWithShadow(textRenderer, k.label, px + 9, y + 4, TEXT);
            String value = listeningKey == k ? "..." : keyName(k.key());
            ctx.drawTextWithShadow(textRenderer, value, px + w - 8 - textRenderer.getWidth(value), y + 4,
                    listeningKey == k ? ACCENT_B : TEXT);
            if (live) addHit(px, y, px + w, y + SETTING_H, clipTop, clipBottom, Kind.KEY, m, s, category);
        }
        return y + SETTING_H;
    }

    private void drawTooltip(DrawContext ctx, int mx, int my) {
        if (dragPanel != null || dragging != null) return;
        for (Hit hit : hits) {
            if (hit.kind() != Kind.MODULE || !hit.contains(mx, my)) continue;
            String text = hit.module().description;
            int w = textRenderer.getWidth(text);
            int tx = Math.min(mx + 10, width - w - 10);
            int ty = my + 12;
            rect(ctx, tx - 4, ty - 3, tx + w + 4, ty + 11, 0xF00C0C12);
            ctx.fill(tx - 4, ty + 11, tx + w + 4, ty + 12, ACCENT_A);
            ctx.drawTextWithShadow(textRenderer, text, tx, ty, TEXT);
            return;
        }
    }

    // ---------------------------------------------------------------- input

    private void applySlider(Hit hit, double mouseX) {
        NumSetting n = (NumSetting) hit.setting();
        int x1 = hit.x1() + 8, x2 = hit.x2() - 8;
        double ratio = (mouseX - x1) / (double) Math.max(1, x2 - x1);
        ratio = Math.max(0, Math.min(1, ratio));
        n.set(n.min + ratio * (n.max - n.min));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit hit : hits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            switch (hit.kind()) {
                case HEADER -> {
                    if (button == 0) {
                        int[] p = pos(hit.category());
                        dragPanel = hit.category();
                        dragOffX = (int) mouseX - p[0];
                        dragOffY = (int) mouseY - p[1];
                    } else if (button == 1) {
                        if (!COLLAPSED.remove(hit.category())) COLLAPSED.add(hit.category());
                    }
                }
                case MODULE -> {
                    if (button == 0) hit.module().toggle();
                    else if (button == 1) hit.module().expanded = !hit.module().expanded;
                    else if (button == 2) listeningBind = hit.module();
                }
                case BOOL -> {
                    BoolSetting b = (BoolSetting) hit.setting();
                    if (button == 0) {
                        b.toggle();
                        Config.save();
                    } else if (button == 1 && !b.children.isEmpty()) {
                        b.open = !b.open;
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
                case KEY -> {
                    if (button == 0) listeningKey = (KeySetting) hit.setting();
                }
                case BIND -> listeningBind = hit.module();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragPanel != null) {
            int[] p = pos(dragPanel);
            p[0] = Math.max(0, Math.min(width - PANEL_W, (int) mouseX - dragOffX));
            p[1] = Math.max(0, Math.min(height - HEADER_H, (int) mouseY - dragOffY));
            return true;
        }
        if (dragging != null) {
            applySlider(dragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null || dragPanel != null) {
            dragging = null;
            dragPanel = null;
            Config.save();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (Category category : Category.values()) {
            int[] p = pos(category);
            int bottom = p[1] + HEADER_H + bodyHeights.getOrDefault(category, 0);
            if (mouseX >= p[0] && mouseX < p[0] + PANEL_W && mouseY >= p[1] && mouseY < bottom) {
                scroll.merge(category, (float) (-verticalAmount * 18), Float::sum);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningKey != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                listeningKey.set(-1);
            } else {
                listeningKey.set(keyCode);
            }
            listeningKey = null;
            Config.save();
            return true;
        }
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
