/*
 * Ported from ArrowAddon (MIT License, (c) Nhân-Tài (ArrowAddon)), see THIRD_PARTY_NOTICES/ArrowAddon-LICENSE.txt.
 */
package abcxyz.render;

import abcxyz.compat.Color;
import abcxyz.compat.Render3DEvent;
import abcxyz.compat.ShapeMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;

public final class SusZoneFx {
    private static final float FADE_IN = 0.3f;
    private static final float FADE_OUT = 0.45f;
    private static final float GROW_TIME = 0.9f;
    private final List<Zone> zones = new ArrayList<Zone>();
    private long lastNanos = 0L;
    private float dt = 0.016f;
    private boolean sawNew = false;

    public void clear() {
        this.zones.clear();
        this.lastNanos = 0L;
    }

    public void begin() {
        long now = System.nanoTime();
        this.dt = this.lastNanos == 0L ? 0.016f : Math.min(0.1f, (float)(now - this.lastNanos) / 1.0E9f);
        this.lastNanos = now;
        this.sawNew = false;
        for (Zone z : this.zones) {
            z.touched = false;
        }
    }

    public void touch(double x, double z, double y, double matchRadius) {
        double ax = Math.floor(x / 16.0) * 16.0 + 8.0;
        double az = Math.floor(z / 16.0) * 16.0 + 8.0;
        Zone best = null;
        double bestD = Double.MAX_VALUE;
        for (Zone zn : this.zones) {
            double d;
            if (zn.touched || !((d = Math.hypot(zn.ax - ax, zn.az - az)) <= matchRadius) || !(d < bestD)) continue;
            bestD = d;
            best = zn;
        }
        if (best == null) {
            best = new Zone(ax, az, y);
            this.zones.add(best);
            this.sawNew = true;
        }
        best.touched = true;
        best.y = y;
    }

    public void render(Render3DEvent event, Style st) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            this.zones.clear();
            return;
        }
        double px = mc.player != null ? mc.player.getX() : 0.0;
        double pz = mc.player != null ? mc.player.getZ() : 0.0;
        double hide = st.hideDistance;
        double bottom = mc.world.getBottomY();
        double t = (double)System.nanoTime() / 1.0E9;
        Vec3d origin = event.center;
        Iterator<Zone> it = this.zones.iterator();
        while (it.hasNext()) {
            double top;
            Zone z = it.next();
            boolean inRange = Math.hypot(z.ax - px, z.az - pz) <= hide;
            boolean show = z.touched && inRange;
            z.alpha = z.alpha + (show ? this.dt / 0.3f : -this.dt / 0.45f);
            z.alpha = Math.max(0.0f, Math.min(1.0f, z.alpha));
            if (show) {
                z.grow = Math.min(1.0f, z.grow + this.dt / 0.9f);
            } else if (z.alpha <= 0.0f) {
                z.grow = 0.0f;
            }
            if (!z.touched && z.alpha <= 0.0f) {
                it.remove();
                continue;
            }
            if (z.alpha <= 0.0f) continue;
            double e = SusZoneFx.ease(z.grow);
            double a = z.alpha;
            double hx = (double)st.width * 0.5 * (0.55 + 0.45 * e);
            double hz = (double)st.length * 0.5 * (0.55 + 0.45 * e);
            double x1 = z.ax - hx;
            double x2 = z.ax + hx;
            double z1 = z.az - hz;
            double z2 = z.az + hz;
            double y = z.y;
            Color fill = new Color(st.r, st.g, st.b, SusZoneFx.clamp((double)st.fillAlpha * a));
            Color line = new Color(st.r, st.g, st.b, SusZoneFx.clamp((double)st.lineAlpha * a));
            event.renderer.quadHorizontal(x1, y, z1, x2, z2, fill);
            event.renderer.line(x1, y, z1, x2, y, z1, line);
            event.renderer.line(x2, y, z1, x2, y, z2, line);
            event.renderer.line(x2, y, z2, x1, y, z2, line);
            event.renderer.line(x1, y, z2, x1, y, z1, line);
            if (st.pillar && (top = bottom + (st.top - bottom) * e) > bottom + 0.5) {
                double pulse = 0.82 + 0.18 * Math.sin(t * 2.2 + z.ax * 0.013);
                SusZoneFx.pillarBox(event, z.ax, z.az, bottom, top, 0.35, st, SusZoneFx.clamp(210.0 * a), SusZoneFx.clamp(255.0 * a), ShapeMode.Both);
                SusZoneFx.pillarBox(event, z.ax, z.az, bottom, top, 0.85, st, SusZoneFx.clamp(60.0 * a * pulse), 0, ShapeMode.Sides);
                SusZoneFx.pillarBox(event, z.ax, z.az, bottom, top, 1.5, st, SusZoneFx.clamp(26.0 * a * pulse), 0, ShapeMode.Sides);
            }
            if (!st.tracer || mc.player == null) continue;
            Color tr = new Color(st.r, st.g, st.b, SusZoneFx.clamp(255.0 * a));
            event.renderer.line(origin.x, origin.y, origin.z, z.ax, y, z.az, tr);
        }
        if (this.sawNew) {
            SusSound.playFoundSound();
        }
    }

    private static void pillarBox(Render3DEvent event, double cx, double cz, double y1, double y2, double half, Style st, int sideA, int lineA, ShapeMode mode) {
        Color side = new Color(st.r, st.g, st.b, sideA);
        Color line = new Color(st.r, st.g, st.b, lineA);
        event.renderer.box(cx - half, y1, cz - half, cx + half, y2, cz + half, side, line, mode, 0);
    }

    private static double ease(float x) {
        double k = 1.0 - (double)x;
        return 1.0 - k * k * k;
    }

    private static int clamp(double a) {
        return Math.max(0, Math.min(255, (int)Math.round(a)));
    }

    private static final class Zone {
        final double ax;
        final double az;
        double y;
        float alpha;
        float grow;
        boolean touched;

        Zone(double ax, double az, double y) {
            this.ax = ax;
            this.az = az;
            this.y = y;
        }
    }

    public static final class Style {
        public int r = 255;
        public int g = 255;
        public int b = 255;
        public int width = 54;
        public int length = 64;
        public int fillAlpha = 65;
        public int lineAlpha = 230;
        public double rectY = 60.0;
        public double top = 160.0;
        public boolean pillar = true;
        public boolean tracer = false;
        public int hideDistance = 850;
    }
}

