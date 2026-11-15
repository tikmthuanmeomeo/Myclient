package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.render.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shows the health of other players above their head and in a list on the left of the screen. */
public class HealthIndicator extends Module {
    private final NumSetting range = add(new NumSetting("Range", 64, 8, 256, 8));
    private final BoolSetting world = add(new BoolSetting("AboveHead", true));
    private final BoolSetting list = add(new BoolSetting("List", true));

    public HealthIndicator() {
        super("HealthIndicator", "Shows the health of other players", Category.Render);
    }

    private static int color(float hp, float max) {
        float ratio = max <= 0 ? 0 : hp / max;
        if (ratio > 0.66f) return 0xFF55FF55;
        if (ratio > 0.33f) return 0xFFFFFF55;
        return 0xFFFF5555;
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
    public void onRender3D(WorldRenderContext context) {
        if (!world.on()) return;
        float delta = context.tickCounter().getTickDelta(true);
        for (AbstractClientPlayerEntity p : nearby()) {
            float hp = p.getHealth() + p.getAbsorptionAmount();
            Vec3d pos = p.getLerpedPos(delta).add(0, p.getHeight() + 0.85, 0);
            Render3D.text(context, String.format("%.1f HP", hp), pos, color(hp, p.getMaxHealth()));
        }
    }

    @Override
    public void onHud(DrawContext context) {
        if (!list.on() || mc.world == null) return;
        int y = 16;
        int shown = 0;
        for (AbstractClientPlayerEntity p : nearby()) {
            if (shown++ >= 8) break;
            float hp = p.getHealth() + p.getAbsorptionAmount();
            String text = p.getName().getString() + "  " + String.format("%.1f", hp);
            context.drawTextWithShadow(mc.textRenderer, text, 4, y, color(hp, p.getMaxHealth()));
            y += 10;
        }
    }
}
