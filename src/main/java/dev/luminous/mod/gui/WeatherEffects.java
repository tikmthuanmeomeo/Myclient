package dev.luminous.mod.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.api.utils.Wrapper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rain and lightning drawn behind the ClickGui panels.
 */
public final class WeatherEffects implements Wrapper {
    private static final Random RNG = new Random();
    private static final List<Drop> DROPS = new ArrayList<>();
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static long lastFrame = 0L;
    private static long nextStrike = 0L;
    private static long lastStrikeTime = 0L;
    private static long nextRainSound = 0L;
    private static long thunderAt = 0L;
    private static float thunderVolume = 0.6f;

    private WeatherEffects() {
    }

    private static final class Drop {
        float x, y, speed, len;
    }

    private static final class Bolt {
        final List<float[]> main = new ArrayList<>();
        final List<List<float[]>> branches = new ArrayList<>();
        long born;
        long life = 560;
    }

    public static void render(DrawContext ctx, int w, int h, boolean rainOn, boolean lightningOn, int rainAmount, float intensity, boolean soundOn, float volume) {
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        if (soundOn && volume > 0f) {
            if (rainOn && now >= nextRainSound) {
                // campfire crackle at a high pitch sounds like something sizzling in a pan
                nextRainSound = now + 90 + RNG.nextInt(170);
                float pitch = 1.5f + RNG.nextFloat() * 0.6f;
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_CAMPFIRE_CRACKLE, pitch, volume * 0.5f));
            }
            if (thunderAt != 0L && now >= thunderAt) {
                thunderAt = 0L;
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f + RNG.nextFloat() * 0.2f, thunderVolume));
            }
        }
        if (rainOn) {
            drawRain(ctx, w, h, rainAmount, dt);
        }
        if (lightningOn) {
            if (nextStrike == 0L) nextStrike = now + 900;
            if (now >= nextStrike) {
                BOLTS.add(makeBolt(w, h, now));
                lastStrikeTime = now;
                if (soundOn && volume > 0f) {
                    mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f + RNG.nextFloat() * 0.2f, volume * 0.7f));
                    thunderVolume = volume;
                    thunderAt = now + 250 + RNG.nextInt(500);
                }
                nextStrike = now + (long) ((2200 + RNG.nextInt(5200)) / Math.max(0.3f, intensity));
            }
            drawBolts(ctx, w, h, now);
        }
    }

    // ---------------------------------------------------------------- rain

    private static void drawRain(DrawContext ctx, int w, int h, int amount, float dt) {
        while (DROPS.size() < amount) {
            Drop d = new Drop();
            resetDrop(d, w, h, true);
            DROPS.add(d);
        }
        while (DROPS.size() > amount) {
            DROPS.remove(DROPS.size() - 1);
        }
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        final int tail = argb(0, 150, 200, 255);
        final int head = argb(120, 170, 215, 255);
        for (Drop d : DROPS) {
            d.y += d.speed * dt;
            d.x -= d.speed * 0.12f * dt;
            if (d.y - d.len > h || d.x < -20) {
                resetDrop(d, w, h, false);
            }
            float dx = d.len * 0.12f;
            b.vertex(m, d.x + dx, d.y - d.len, 0f).color(tail);
            b.vertex(m, d.x + dx + 1f, d.y - d.len, 0f).color(tail);
            b.vertex(m, d.x + 1f, d.y, 0f).color(head);
            b.vertex(m, d.x, d.y, 0f).color(head);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void resetDrop(Drop d, int w, int h, boolean anywhere) {
        d.x = RNG.nextFloat() * (w + 60);
        d.y = anywhere ? RNG.nextFloat() * h : -RNG.nextFloat() * 40f;
        d.speed = 380f + RNG.nextFloat() * 320f;
        d.len = 7f + RNG.nextFloat() * 11f;
    }

    // ------------------------------------------------------------ lightning

    private static Bolt makeBolt(int w, int h, long now) {
        Bolt bolt = new Bolt();
        bolt.born = now;
        float x = w * (0.12f + RNG.nextFloat() * 0.76f);
        float y = 0f;
        float endY = h * (0.38f + RNG.nextFloat() * 0.25f);
        bolt.main.add(new float[]{x, y});
        while (y < endY) {
            y += 9f + RNG.nextFloat() * 20f;
            x += (RNG.nextFloat() - 0.5f) * 26f;
            bolt.main.add(new float[]{x, y});
        }
        int branchCount = 1 + RNG.nextInt(2);
        for (int i = 0; i < branchCount; i++) {
            int from = 2 + RNG.nextInt(Math.max(1, bolt.main.size() - 4));
            float[] start = bolt.main.get(Math.min(from, bolt.main.size() - 1));
            List<float[]> br = new ArrayList<>();
            float bx = start[0], by = start[1];
            float dir = RNG.nextBoolean() ? 1f : -1f;
            br.add(new float[]{bx, by});
            int segs = 3 + RNG.nextInt(4);
            for (int s = 0; s < segs; s++) {
                bx += dir * (5f + RNG.nextFloat() * 12f);
                by += 6f + RNG.nextFloat() * 14f;
                br.add(new float[]{bx, by});
            }
            bolt.branches.add(br);
        }
        return bolt;
    }

    private static void drawBolts(DrawContext ctx, int w, int h, long now) {
        // screen flash right after a strike
        long sinceStrike = now - lastStrikeTime;
        if (sinceStrike >= 0 && sinceStrike < 110) {
            int a = (int) (34 * (1f - sinceStrike / 110f));
            if (a > 0) ctx.fill(0, 0, w, h, argb(a, 190, 225, 255));
        }

        BOLTS.removeIf(b -> now - b.born > b.life);
        for (Bolt bolt : BOLTS) {
            long age = now - bolt.born;
            float a;
            if (age < 70) a = 1f;
            else if (age < 120) a = 0.3f;
            else if (age < 190) a = 0.95f;
            else a = Math.max(0f, 1f - (age - 190f) / (bolt.life - 190f));

            drawPolyline(ctx, bolt.main, 10f, argb((int) (45 * a), 40, 170, 255));
            drawPolyline(ctx, bolt.main, 3.6f, argb((int) (170 * a), 70, 210, 255));
            drawPolyline(ctx, bolt.main, 1.6f, argb((int) (255 * a), 235, 250, 255));
            for (List<float[]> br : bolt.branches) {
                drawPolyline(ctx, br, 5f, argb((int) (40 * a), 40, 170, 255));
                drawPolyline(ctx, br, 1.4f, argb((int) (210 * a), 190, 235, 255));
            }
        }
    }

    private static void drawPolyline(DrawContext ctx, List<float[]> pts, float width, int color) {
        if (pts.size() < 2 || (color >>> 24) <= 1) return;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float hw = width / 2f;
        for (int i = 0; i < pts.size() - 1; i++) {
            float[] p0 = pts.get(i);
            float[] p1 = pts.get(i + 1);
            b.vertex(m, p0[0] - hw, p0[1], 0f).color(color);
            b.vertex(m, p0[0] + hw, p0[1], 0f).color(color);
            b.vertex(m, p1[0] + hw, p1[1], 0f).color(color);
            b.vertex(m, p1[0] - hw, p1[1], 0f).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }
}
