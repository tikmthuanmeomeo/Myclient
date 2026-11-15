package abcxyz.module.render;

import abcxyz.AbcxyzClient;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.ModeSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shows the name of the client and the list of enabled modules at the top of the screen. */
public class ModuleList extends Module {
    private final ModeSetting position = add(new ModeSetting("Position", "Right", "Right", "Left"));
    private final BoolSetting watermark = add(new BoolSetting("Watermark", true));
    private final BoolSetting extra = add(new BoolSetting("Info", true));

    public ModuleList() {
        super("ModuleList", "Shows enabled modules at the top of the screen", Category.Render);
    }

    private static int rainbow(int index) {
        float hue = ((System.currentTimeMillis() % 5000L) / 5000f + index * 0.04f) % 1f;
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.55f, 1f);
    }

    @Override
    public void onHud(DrawContext context) {
        if (AbcxyzClient.modules == null) return;
        if (watermark.on()) {
            context.drawTextWithShadow(mc.textRenderer, AbcxyzClient.NAME, 4, 4, rainbow(0));
        }
        List<String> lines = new ArrayList<>();
        for (Module m : AbcxyzClient.modules.modules) {
            if (!m.isEnabled() || m == this) continue;
            String text = m.name;
            if (extra.on() && m.info() != null) text += " \u00a77" + m.info();
            lines.add(text);
        }
        lines.sort(Comparator.comparingInt((String s) -> mc.textRenderer.getWidth(s)).reversed());
        int width = context.getScaledWindowWidth();
        boolean right = position.is("Right");
        int y = 3;
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            int w = mc.textRenderer.getWidth(text);
            int x = right ? width - w - 5 : 4;
            if (!right && watermark.on() && i == 0) y = 15;
            int color = rainbow(i);
            context.fill(right ? x - 2 : x - 2, y - 1, right ? x + w + 2 : x + w + 2, y + 9, 0x70000000);
            context.fill(right ? x + w + 2 : x - 3, y - 1, right ? x + w + 3 : x - 2, y + 9, color);
            context.drawTextWithShadow(mc.textRenderer, text, x, y, color);
            y += 10;
        }
    }
}
