package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws one heart icon per heart of health above other players (and optionally mobs), stacked in rows of ten,
 * like the vanilla heart bar: full, half and empty hearts, plus gold hearts for absorption.
 * The hearts are small pixel pictures drawn as coloured squares that face the camera.
 */
public class HealthIndicator extends Module {
    private enum Kind {FULL, HALF, EMPTY, GOLD, GOLD_HALF}

    private static final String[] HEART = {
            ".##...##.",
            "#HRR#RRR#",
            "#HRRRRRR#",
            "#RRRRRRR#",
            ".#RRRRR#.",
            "..#RRR#..",
            "...#R#...",
            "....#...."
    };
    private static final int OUTLINE = 0xFF260000;
    private static final int RED = 0xFFFF1A1A;
    private static final int RED_HIGHLIGHT = 0xFFFFB4B4;
    private static final int EMPTY_FILL = 0xFF3A1212;
    private static final int GOLD_OUTLINE = 0xFF4A3800;
    private static final int GOLD = 0xFFFFC107;
    private static final int GOLD_HIGHLIGHT = 0xFFFFF2A8;

    private static final int PER_ROW = 10;
    private static final int MAX_HEARTS = 100;
    private static final int HEART_SPACING = 8;
    private static final int ROW_HEIGHT = 9;
    private static final int MAX_ENTITIES = 40;

    /** For every kind of heart: runs of one colour as {x, y, width, colour}. */
    private static final int[][][] SHAPES = new int[Kind.values().length][][];

    static {
        for (Kind kind : Kind.values()) SHAPES[kind.ordinal()] = buildShape(kind);
    }

    private final NumSetting range = add(new NumSetting("Range", 64, 8, 256, 8));
    private final NumSetting size = add(new NumSetting("Size", 1.0, 0.5, 3.0, 0.1));
    private final NumSetting height = add(new NumSetting("Height", 0.6, 0.0, 2.0, 0.05));
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting mobs = add(new BoolSetting("Mobs", false));
    private final BoolSetting throughWalls = add(new BoolSetting("ThroughWalls", true));

    public HealthIndicator() {
        super("HealthIndicator", "Shows hearts above other players like the vanilla health bar", Category.Render);
    }

    private static int cellColor(Kind kind, char cell, int x) {
        boolean gold = kind == Kind.GOLD || kind == Kind.GOLD_HALF;
        boolean left = x <= 4;
        switch (cell) {
            case '#':
                return gold ? GOLD_OUTLINE : OUTLINE;
            case 'H':
                if (kind == Kind.EMPTY) return EMPTY_FILL;
                return gold ? GOLD_HIGHLIGHT : RED_HIGHLIGHT;
            case 'R':
                if (kind == Kind.EMPTY) return EMPTY_FILL;
                if ((kind == Kind.HALF || kind == Kind.GOLD_HALF) && !left) return EMPTY_FILL;
                return gold ? GOLD : RED;
            default:
                return 0;
        }
    }

    private static int[][] buildShape(Kind kind) {
        List<int[]> runs = new ArrayList<>();
        for (int y = 0; y < HEART.length; y++) {
            String row = HEART[y];
            int x = 0;
            while (x < row.length()) {
                int color = cellColor(kind, row.charAt(x), x);
                if (color == 0) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < row.length() && cellColor(kind, row.charAt(x), x) == color) x++;
                runs.add(new int[]{start, y, x - start, color});
            }
        }
        return runs.toArray(new int[0][]);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float x, float y, float w, float h, int argb) {
        int a = (argb >> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, bl = argb & 255;
        b.vertex(m, x, y, 0f).color(r, g, bl, a);
        b.vertex(m, x, y + h, 0f).color(r, g, bl, a);
        b.vertex(m, x + w, y + h, 0f).color(r, g, bl, a);
        b.vertex(m, x + w, y, 0f).color(r, g, bl, a);
    }

    private boolean wanted(Entity e) {
        if (e == mc.player || !(e instanceof LivingEntity living) || !living.isAlive() || e instanceof ArmorStandEntity) return false;
        if (e instanceof PlayerEntity) return players.on();
        return mobs.on();
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        float delta = context.tickCounter().getTickDelta(true);
        Matrix4f base = context.matrixStack().peek().getPositionMatrix();

        RenderSystem.disableCull();
        if (throughWalls.on()) RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int drawn = 0;
        for (Entity e : mc.world.getEntities()) {
            if (drawn >= MAX_ENTITIES) break;
            if (!wanted(e) || mc.player.distanceTo(e) > range.d()) continue;
            LivingEntity living = (LivingEntity) e;

            List<Kind> hearts = new ArrayList<>();
            int maxHearts = Math.max(1, Math.min(MAX_HEARTS, (int) Math.ceil(living.getMaxHealth() / 2.0f)));
            for (int i = 0; i < maxHearts; i++) {
                float left = living.getHealth() - i * 2.0f;
                hearts.add(left >= 2.0f ? Kind.FULL : left >= 1.0f ? Kind.HALF : Kind.EMPTY);
            }
            float absorption = living.getAbsorptionAmount();
            int gold = Math.min(20, (int) Math.ceil(absorption / 2.0f));
            for (int i = 0; i < gold; i++) {
                hearts.add(absorption - i * 2.0f >= 2.0f ? Kind.GOLD : Kind.GOLD_HALF);
            }

            Vec3d pos = e.getLerpedPos(delta).add(0, e.getHeight() + height.d(), 0);
            double dist = Math.max(1.0, cam.distanceTo(pos));
            float scale = (float) (Math.max(0.012, dist * 0.003) * size.d());
            Matrix4f m = new Matrix4f(base)
                    .translate((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z))
                    .rotate(camera.getRotation())
                    .scale(-scale, -scale, scale);

            for (int row = 0; row * PER_ROW < hearts.size(); row++) {
                int from = row * PER_ROW;
                int count = Math.min(PER_ROW, hearts.size() - from);
                float width = (count - 1) * HEART_SPACING + 9;
                float startX = -width / 2f;
                float startY = -(row + 1) * ROW_HEIGHT;
                for (int j = 0; j < count; j++) {
                    float heartX = startX + j * HEART_SPACING;
                    for (int[] run : SHAPES[hearts.get(from + j).ordinal()]) {
                        quad(b, m, heartX + run[0], startY + run[1], run[2], 1f, run[3]);
                    }
                }
            }
            drawn++;
        }

        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
