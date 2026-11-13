package dev.luminous.mod.gui;

import dev.luminous.mod.modules.settings.impl.BlockListSetting;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Block picker: tap a block to add it to / remove it from the list. Drag or scroll to move, type to search. */
public class BlockListScreen extends Screen {
    private static final int CELL = 22;
    private static final int TOP = 44;

    private final BlockListSetting setting;
    private final List<Block> all = new ArrayList<>();
    private List<Block> shown = new ArrayList<>();
    private String search = "";
    private float scroll = 0f;
    private double pressX, pressY;
    private boolean dragged;

    public BlockListScreen(BlockListSetting setting) {
        super(Text.literal("Select blocks"));
        this.setting = setting;
        for (Block block : Registries.BLOCK) {
            if (block.asItem() == Items.AIR) continue;
            all.add(block);
        }
        all.sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).getPath()));
        refilter();
    }

    private void refilter() {
        String q = search.toLowerCase().trim().replace(' ', '_');
        List<Block> list = new ArrayList<>();
        for (Block block : all) {
            if (q.isEmpty() || Registries.BLOCK.getId(block).getPath().contains(q)) list.add(block);
        }
        shown = list;
        scroll = 0f;
    }

    private int cols() {
        return Math.max(1, (this.width - 20) / CELL);
    }

    private int gridLeft() {
        return (this.width - cols() * CELL) / 2;
    }

    private int visibleRows() {
        return Math.max(1, (this.height - TOP - 6) / CELL);
    }

    private float maxScroll() {
        int rows = (shown.size() + cols() - 1) / cols();
        return Math.max(0, rows - visibleRows());
    }

    private void clampScroll() {
        scroll = Math.max(0f, Math.min(maxScroll(), scroll));
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, int mx, int my) {
        ctx.fill(x, y, x + w, y + h, inside(mx, my, x, y, w, h) ? 0xFF2E6FB5 : 0xFF1F4F85);
        ctx.drawBorder(x, y, w, h, 0xFF4DB8FF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, label, x + w / 2, y + 4, 0xFFFFFFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xEE0A1020);
        ctx.drawCenteredTextWithShadow(this.textRenderer, setting.getName() + "  (" + setting.size() + " selected)", this.width / 2, 7, 0xFFFFFFFF);
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        ctx.drawTextWithShadow(this.textRenderer, "Search: " + search + (blink ? "_" : ""), 10, 25, 0xFFB8D8FF);
        button(ctx, this.width - 135, 20, 60, 16, "Clear", mouseX, mouseY);
        button(ctx, this.width - 70, 20, 60, 16, "Done", mouseX, mouseY);

        clampScroll();
        int cols = cols();
        int left = gridLeft();
        int firstRow = (int) scroll;
        Block hovered = null;
        for (int r = 0; r < visibleRows(); r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (firstRow + r) * cols + c;
                if (idx >= shown.size()) break;
                Block block = shown.get(idx);
                int cx = left + c * CELL;
                int cy = TOP + r * CELL;
                boolean selected = setting.contains(block);
                ctx.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, selected ? 0xAA1F78C8 : 0x33203050);
                if (selected) ctx.drawBorder(cx, cy, CELL - 2, CELL - 2, 0xFF4DB8FF);
                ctx.drawItem(new ItemStack(block.asItem()), cx + 2, cy + 2);
                if (inside(mouseX, mouseY, cx, cy, CELL - 2, CELL - 2)) hovered = block;
            }
        }
        if (shown.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, "No block found", this.width / 2, TOP + 20, 0xFF8899AA);
        }
        if (hovered != null) {
            ctx.drawTooltip(this.textRenderer, hovered.getName(), mouseX, mouseY);
        }
    }

    private void click(double mx, double my) {
        if (inside(mx, my, this.width - 70, 20, 60, 16)) {
            close();
            return;
        }
        if (inside(mx, my, this.width - 135, 20, 60, 16)) {
            setting.clear();
            return;
        }
        int cols = cols();
        int col = (int) Math.floor((mx - gridLeft()) / CELL);
        int row = (int) Math.floor((my - TOP) / CELL);
        if (mx < gridLeft() || my < TOP || col < 0 || col >= cols || row < 0 || row >= visibleRows()) return;
        int idx = ((int) scroll + row) * cols + col;
        if (idx >= 0 && idx < shown.size()) setting.toggle(shown.get(idx));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        pressX = mouseX;
        pressY = mouseY;
        dragged = false;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (Math.abs(mouseX - pressX) > 4 || Math.abs(mouseY - pressY) > 4) dragged = true;
        if (dragged) {
            scroll -= (float) (deltaY / CELL);
            clampScroll();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !dragged) click(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (float) (verticalAmount * 1.5);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                refilter();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr >= 32 && chr != 127) {
            search += chr;
            refilter();
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(ClickGuiScreen.getInstance());
    }
}
