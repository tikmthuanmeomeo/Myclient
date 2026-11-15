package abcxyz.module.render;

import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.render.Render3D;
import abcxyz.render.Render3D.Part;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws one heart per heart of health above the head of other players, in the world:
 * red = full, pink = half, grey = empty, gold = absorption.
 */
public class HealthIndicator extends Module {
    private static final String HEART = "\u2665";
    private static final int FULL = 0xFFFF2B2B;
    private static final int HALF = 0xFFFF9AA2;
    private static final int EMPTY = 0xFF4A4A4A;
    private static final int GOLD = 0xFFFFD700;
    private static final int PER_ROW = 10;
    private static final int MAX_ROWS = 3;

    private final NumSetting range = add(new NumSetting("Range", 64, 8, 256, 8));
    private final NumSetting size = add(new NumSetting("Size", 1.0, 0.5, 3.0, 0.1));

    public HealthIndicator() {
        super("HealthIndicator", "Shows one heart per heart above other players", Category.Render);
    }

    /** Appends hearts to a row list; each element of states is 2 = full, 1 = half, 0 = empty. */
    private static List<List<Part>> rows(List<int[]> hearts) {
        List<List<Part>> rows = new ArrayList<>();
        for (int i = 0; i < hearts.size() && rows.size() < MAX_ROWS; i += PER_ROW) {
            List<Part> row = new ArrayList<>();
            // merge neighbours with the same colour into one piece of text
            StringBuilder run = new StringBuilder();
            int runColor = 0;
            for (int j = i; j < Math.min(hearts.size(), i + PER_ROW); j++) {
                int[] heart = hearts.get(j);
                int color = heart[1];
                if (run.length() > 0 && color != runColor) {
                    row.add(new Part(run.toString(), runColor));
                    run.setLength(0);
                }
                runColor = color;
                run.append(HEART);
            }
            if (run.length() > 0) row.add(new Part(run.toString(), runColor));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        float delta = context.tickCounter().getTickDelta(true);
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            if (mc.player.distanceTo(p) > range.d()) continue;

            float health = p.getHealth();
            int maxHearts = Math.max(1, (int) Math.ceil(p.getMaxHealth() / 2.0f));
            List<int[]> hearts = new ArrayList<>();
            for (int i = 0; i < maxHearts; i++) {
                float left = health - i * 2.0f;
                if (left >= 2.0f) hearts.add(new int[]{2, FULL});
                else if (left >= 1.0f) hearts.add(new int[]{1, HALF});
                else hearts.add(new int[]{0, EMPTY});
            }
            float absorption = p.getAbsorptionAmount();
            int goldHearts = (int) Math.ceil(absorption / 2.0f);
            for (int i = 0; i < goldHearts; i++) hearts.add(new int[]{2, GOLD});

            Vec3d pos = p.getLerpedPos(delta).add(0, p.getHeight() + 0.25, 0);
            Render3D.textRows(context, rows(hearts), pos, size.d());
        }
    }
}
