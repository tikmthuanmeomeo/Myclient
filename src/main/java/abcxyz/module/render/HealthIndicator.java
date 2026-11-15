package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.render.Projection;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shows how many hearts other players have, right above their heads (not a nametag). */
public class HealthIndicator extends Module {
    private final NumSetting range = add(new NumSetting("Range", 64, 8, 256, 8));
    private final NumSetting size = add(new NumSetting("Size", 1.0, 0.5, 2.5, 0.1));
    private final BoolSetting list = add(new BoolSetting("List", false));

    public HealthIndicator() {
        super("HealthIndicator", "Shows the hearts of other players above their heads", Category.Render);
    }

    private static int color(float hp, float max) {
        float ratio = max <= 0 ? 0 : hp / max;
        if (ratio > 0.66f) return 0xFF55FF55;
        if (ratio > 0.33f) return 0xFFFFFF55;
        return 0xFFFF5555;
    }

    private static String hearts(float health) {
        float h = health / 2.0f;
        return h == Math.floor(h) ? String.valueOf((int) h) : String.format("%.1f", h);
    }

    private List<AbstractClientPlayerEntity> nearby() {
        List<AbstractClientPlayerEntity> result = new ArrayList<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            if (mc.player.distanceTo(p) > range.d()) continue;
            result.add(p);
        }
        result.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));
        return result;
    }

    @Override
    public void onHud(DrawContext context) {
        if (mc.world == null) return;
        float delta = Projection.tickDelta();
        TextRenderer tr = mc.textRenderer;
        List<AbstractClientPlayerEntity> players = nearby();
        for (AbstractClientPlayerEntity p : players) {
            Vec3d above = p.getLerpedPos(delta).add(0, p.getHeight() + 0.3, 0);
            double[] screen = Projection.toScreen(above);
            if (screen == null) continue;
            float hp = p.getHealth();
            float absorption = p.getAbsorptionAmount();
            String heart = "\u2665";
            String number = " " + hearts(hp);
            String extra = absorption > 0 ? " +" + hearts(absorption) : "";
            int total = tr.getWidth(heart + number + extra);
            MatrixStack ms = context.getMatrices();
            ms.push();
            ms.translate((float) screen[0], (float) screen[1], 0f);
            ms.scale((float) size.d(), (float) size.d(), 1f);
            int x = -total / 2;
            int y = -9;
            context.drawTextWithShadow(tr, heart, x, y, 0xFFFF3B3B);
            context.drawTextWithShadow(tr, number, x + tr.getWidth(heart), y, color(hp, p.getMaxHealth()));
            if (!extra.isEmpty()) {
                context.drawTextWithShadow(tr, extra, x + tr.getWidth(heart + number), y, 0xFFFFD700);
            }
            ms.pop();
        }
        if (list.on()) {
            int y = 16;
            int shown = 0;
            for (AbstractClientPlayerEntity p : players) {
                if (shown++ >= 8) break;
                String text = p.getName().getString() + "  \u2665 " + hearts(p.getHealth());
                context.drawTextWithShadow(tr, text, 4, y, color(p.getHealth(), p.getMaxHealth()));
                y += 10;
            }
        }
    }
}
