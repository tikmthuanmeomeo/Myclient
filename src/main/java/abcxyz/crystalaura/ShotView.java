/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.crystalaura;

import abcxyz.compat.Color;
import abcxyz.compat.Render3DEvent;
import abcxyz.compat.ShapeMode;
import abcxyz.render.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Locale;

/** Shows the block the crystal aura is working on (a box that slides to the next spot) with the damage dealt. */
public final class ShotView {
    private static final long SLIDE_MS = 120L;
    private static final long HOLD_MS = 150L;
    private static final long FADE_MS = 350L;
    private static final float FILL_ALPHA = 0.22f;
    private static final double LABEL_RISE = 0.25;
    private Box from;
    private Box to;
    private long movedAt;
    private long shownAt;
    private int color;
    private float damage;
    private float selfDamage;

    public void show(Box box, int color, float damage, float selfDamage) {
        long now = System.currentTimeMillis();
        if (!this.showing(now)) {
            this.from = box;
            this.movedAt = now;
        } else if (!box.equals((Object)this.to)) {
            this.from = this.box(now);
            this.movedAt = now;
        }
        this.to = box;
        this.shownAt = now;
        this.color = color;
        this.damage = damage;
        this.selfDamage = selfDamage;
    }

    public void clear() {
        this.to = null;
    }

    public boolean showing() {
        return this.showing(System.currentTimeMillis());
    }

    public void draw(Render3DEvent event) {
        long now = System.currentTimeMillis();
        if (!this.showing(now)) {
            return;
        }
        float alpha = this.alpha(now);
        Box b = this.box(now);
        event.renderer.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                toColor(fade(this.color, alpha * FILL_ALPHA)), toColor(fade(this.color, alpha)), ShapeMode.Both, 0);
    }

    public void label(WorldRenderContext context) {
        long now = System.currentTimeMillis();
        if (!this.showing(now)) {
            return;
        }
        Box box = this.box(now);
        Vec3d top = new Vec3d(box.getCenter().x, box.maxY + LABEL_RISE, box.getCenter().z);
        int a = Math.round(255.0f * this.alpha(now));
        int white = (a << 24) | 0xFFFFFF;
        int red = (a << 24) | 0xFF8080;
        Render3D.textRows(context, List.of(List.of(
                new Render3D.Part(String.format(Locale.ROOT, "%.1f", this.damage), white),
                new Render3D.Part(String.format(Locale.ROOT, "  self %.1f", this.selfDamage), red))), top, 1.0);
    }

    private boolean showing(long now) {
        return this.to != null && this.alpha(now) > 0.0f;
    }

    private float alpha(long now) {
        long idle = now - this.shownAt - HOLD_MS;
        return idle <= 0L ? 1.0f : Math.max(0.0f, 1.0f - (float)idle / (float)FADE_MS);
    }

    private Box box(long now) {
        float t = Math.min(1.0f, (float)(now - this.movedAt) / (float)SLIDE_MS);
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        return new Box(this.from.getMinPos().lerp(this.to.getMinPos(), (double)eased), this.from.getMaxPos().lerp(this.to.getMaxPos(), (double)eased));
    }

    private static int fade(int argb, float alpha) {
        return Math.round((float)(argb >>> 24) * alpha) << 24 | argb & 0xFFFFFF;
    }

    private static Color toColor(int argb) {
        return new Color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }
}
