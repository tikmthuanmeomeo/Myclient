package abcxyz.module.render;

import abcxyz.AbcxyzClient;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.ModeSetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The HUD: client name, list of enabled modules and your inventory. (This replaces the old ModuleList module.)
 * Open the click GUI and drag any of the three parts to move it; positions are saved in the config.
 */
public class HudEdit extends Module {
    public enum Element {
        WATERMARK("Watermark"), LIST("Module list"), INVENTORY("Inventory");

        public final String title;

        Element(String title) {
            this.title = title;
        }
    }

    private final ModeSetting position = add(new ModeSetting("Position", "Right", "Right", "Left"));
    private final BoolSetting extra = add(new BoolSetting("Info", true));
    private final BoolSetting moduleList = add(new BoolSetting("ModuleList", true)).with(position, extra);
    private final BoolSetting watermark = add(new BoolSetting("Watermark", true));
    private final BoolSetting hotbar = add(new BoolSetting("InvHotbar", false));
    private final NumSetting invScale = add(new NumSetting("InvScale", 1.0, 0.5, 2.0, 0.1));
    private final BoolSetting inventory = add(new BoolSetting("Inventory", true)).with(hotbar, invScale);

    /** Saved position of each element as a fraction of the window size; -1 = default place. Hidden in the GUI. */
    private final NumSetting[] posX = new NumSetting[Element.values().length];
    private final NumSetting[] posY = new NumSetting[Element.values().length];

    private final boolean[] shown = new boolean[Element.values().length];
    private final int[][] bounds = new int[Element.values().length][4];
    private final int[][] anchor = new int[Element.values().length][2];
    private String lastPosition = null;

    private Element dragged = null;
    private double dragMouseX, dragMouseY;
    private int dragAnchorX, dragAnchorY;

    public HudEdit() {
        super("HudEdit", "Watermark, module list and inventory on screen. Drag them in the click GUI", Category.Render);
        moduleList.label = "Module list";
        extra.label = "Info";
        hotbar.label = "Hotbar";
        invScale.label = "Scale";
        for (Element e : Element.values()) {
            posX[e.ordinal()] = add(new NumSetting(e.title.replace(" ", "") + "X", -1, -1, 1, 0.0001));
            posY[e.ordinal()] = add(new NumSetting(e.title.replace(" ", "") + "Y", -1, -1, 1, 0.0001));
            posX[e.ordinal()].hidden = true;
            posY[e.ordinal()].hidden = true;
        }
    }

    private static int rainbow(int index) {
        float hue = ((System.currentTimeMillis() % 5000L) / 5000f + index * 0.04f) % 1f;
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.55f, 1f);
    }

    private int anchorX(Element e, int sw, int def) {
        double v = posX[e.ordinal()].d();
        return v < 0 ? def : (int) Math.round(v * sw);
    }

    private int anchorY(Element e, int sh, int def) {
        double v = posY[e.ordinal()].d();
        return v < 0 ? def : (int) Math.round(v * sh);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void onHud(DrawContext context) {
        if (AbcxyzClient.modules == null || mc.player == null) return;
        Arrays.fill(shown, false);
        int sw = context.getScaledWindowWidth(), sh = context.getScaledWindowHeight();
        if (watermark.on()) drawWatermark(context, sw, sh);
        if (moduleList.on()) drawList(context, sw, sh);
        if (inventory.on()) drawInventory(context, sw, sh);
    }

    private void drawWatermark(DrawContext ctx, int sw, int sh) {
        int i = Element.WATERMARK.ordinal();
        int x = anchorX(Element.WATERMARK, sw, 4), y = anchorY(Element.WATERMARK, sh, 4);
        ctx.drawTextWithShadow(mc.textRenderer, AbcxyzClient.NAME, x, y, rainbow(0));
        anchor[i][0] = x;
        anchor[i][1] = y;
        bounds[i] = new int[]{x - 2, y - 2, mc.textRenderer.getWidth(AbcxyzClient.NAME) + 4, 12};
        shown[i] = true;
    }

    private void drawList(DrawContext ctx, int sw, int sh) {
        int idx = Element.LIST.ordinal();
        List<String> lines = new ArrayList<>();
        for (Module m : AbcxyzClient.modules.modules) {
            if (!m.isEnabled() || m == this) continue;
            String text = m.name;
            if (extra.on() && m.info() != null) text += " \u00a77" + m.info();
            lines.add(text);
        }
        lines.sort(Comparator.comparingInt((String s) -> mc.textRenderer.getWidth(s)).reversed());

        boolean right = position.is("Right");
        // when the side is switched the saved position would be wrong, go back to the default place
        if (lastPosition != null && !lastPosition.equals(position.get())) {
            posX[idx].set(-1);
            posY[idx].set(-1);
        }
        lastPosition = position.get();

        int ax = anchorX(Element.LIST, sw, right ? sw - 5 : 4);
        int ay = anchorY(Element.LIST, sh, !right && watermark.on() ? 15 : 3);
        anchor[idx][0] = ax;
        anchor[idx][1] = ay;

        int maxW = 60;
        int y = ay;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int w = mc.textRenderer.getWidth(text);
            maxW = Math.max(maxW, w);
            int x = right ? ax - w : ax;
            int color = rainbow(i);
            ctx.fill(x - 2, y - 1, x + w + 2, y + 9, 0x70000000);
            ctx.fill(right ? x + w + 2 : x - 3, y - 1, right ? x + w + 3 : x - 2, y + 9, color);
            ctx.drawTextWithShadow(mc.textRenderer, text, x, y, color);
            y += 10;
        }
        int height = Math.max(10, lines.size() * 10);
        int left = right ? ax - maxW - 2 : ax - 3;
        bounds[idx] = new int[]{left, ay - 1, maxW + 5, height};
        shown[idx] = true;
    }

    private void drawInventory(DrawContext ctx, int sw, int sh) {
        int idx = Element.INVENTORY.ordinal();
        float scale = (float) invScale.d();
        int rows = hotbar.on() ? 4 : 3;
        int cw = 9 * 18 + 4;
        int ch = rows * 18 + 4 + (hotbar.on() ? 2 : 0);
        int w = Math.round(cw * scale), h = Math.round(ch * scale);
        int x = anchorX(Element.INVENTORY, sw, 6), y = anchorY(Element.INVENTORY, sh, sh / 2 - h / 2);
        anchor[idx][0] = x;
        anchor[idx][1] = y;

        PlayerInventory inv = mc.player.getInventory();
        var matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1f);
        ctx.fill(0, 0, cw, ch, 0x80000000);
        for (int r = 0; r < rows; r++) {
            boolean hotbarRow = r == 3;
            for (int c = 0; c < 9; c++) {
                int slot = hotbarRow ? c : 9 + r * 9 + c;
                int ix = 2 + c * 18, iy = 2 + r * 18 + (hotbarRow ? 2 : 0);
                ctx.fill(ix, iy, ix + 16, iy + 16, 0x28FFFFFF);
                ItemStack stack = inv.getStack(slot);
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, ix, iy);
                    ctx.drawStackOverlay(mc.textRenderer, stack, ix, iy);
                }
            }
        }
        matrices.pop();
        bounds[idx] = new int[]{x, y, w, h};
        shown[idx] = true;
    }

    // ---------------------------------------------------------------- editing (used by the click GUI)

    /** Draws the HUD again on top of the dimmed background and outlines every part that can be dragged. */
    public void drawEditOverlay(DrawContext ctx, int mx, int my) {
        onHud(ctx);
        for (Element e : Element.values()) {
            int i = e.ordinal();
            if (!shown[i]) continue;
            int[] b = bounds[i];
            boolean active = e == dragged || (dragged == null && elementAt(mx, my) == e);
            int color = active ? 0xFFFFFFFF : 0xFF8B5CFF;
            ctx.fill(b[0], b[1], b[0] + b[2], b[1] + 1, color);
            ctx.fill(b[0], b[1] + b[3] - 1, b[0] + b[2], b[1] + b[3], color);
            ctx.fill(b[0], b[1], b[0] + 1, b[1] + b[3], color);
            ctx.fill(b[0] + b[2] - 1, b[1], b[0] + b[2], b[1] + b[3], color);
            ctx.drawTextWithShadow(mc.textRenderer, e.title, b[0] + 2, Math.max(0, b[1] - 10), color);
        }
    }

    public Element elementAt(double mx, double my) {
        Element[] all = Element.values();
        for (int k = all.length - 1; k >= 0; k--) {
            int i = all[k].ordinal();
            if (!shown[i]) continue;
            int[] b = bounds[i];
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return all[k];
        }
        return null;
    }

    public void beginDrag(Element e, double mx, double my) {
        dragged = e;
        dragMouseX = mx;
        dragMouseY = my;
        dragAnchorX = anchor[e.ordinal()][0];
        dragAnchorY = anchor[e.ordinal()][1];
    }

    public void drag(double mx, double my, int sw, int sh) {
        if (dragged == null) return;
        int nx = (int) Math.max(0, Math.min(sw, dragAnchorX + (mx - dragMouseX)));
        int ny = (int) Math.max(0, Math.min(sh, dragAnchorY + (my - dragMouseY)));
        posX[dragged.ordinal()].set((double) nx / sw);
        posY[dragged.ordinal()].set((double) ny / sh);
    }

    public void endDrag() {
        dragged = null;
    }
}
