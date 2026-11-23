package abcxyz.compat;

import abcxyz.render.Render3D;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects lines, flat quads and boxes in world coordinates during one frame and draws them all at the end
 * (through walls), using the Render3D helpers.
 */
public class Renderer3D {
    private record Line(double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
    }

    private record Quad(double[] p, int argb) {
    }

    private final List<Line> lines = new ArrayList<>();
    private final List<Quad> quads = new ArrayList<>();

    public void line(double x1, double y1, double z1, double x2, double y2, double z2, Color c) {
        if (c == null || c.a <= 0) return;
        lines.add(new Line(x1, y1, z1, x2, y2, z2, c.argb()));
    }

    public void quadHorizontal(double x1, double y, double z1, double x2, double z2, Color c) {
        if (c == null || c.a <= 0) return;
        quads.add(new Quad(new double[]{x1, y, z1, x1, y, z2, x2, y, z2, x2, y, z1}, c.argb()));
    }

    private void quad(Color c, double... p) {
        quads.add(new Quad(p, c.argb()));
    }

    /** excludeDir is ignored (all faces are drawn). */
    public void box(double x1, double y1, double z1, double x2, double y2, double z2, Color side, Color line, ShapeMode mode, int excludeDir) {
        if (mode != ShapeMode.Lines && side != null && side.a > 0) {
            quad(side, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2); // bottom
            quad(side, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1); // top
            quad(side, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1); // north
            quad(side, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2); // south
            quad(side, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1); // west
            quad(side, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2); // east
        }
        if (mode != ShapeMode.Sides && line != null && line.a > 0) {
            line(x1, y1, z1, x2, y1, z1, line);
            line(x2, y1, z1, x2, y1, z2, line);
            line(x2, y1, z2, x1, y1, z2, line);
            line(x1, y1, z2, x1, y1, z1, line);
            line(x1, y2, z1, x2, y2, z1, line);
            line(x2, y2, z1, x2, y2, z2, line);
            line(x2, y2, z2, x1, y2, z2, line);
            line(x1, y2, z2, x1, y2, z1, line);
            line(x1, y1, z1, x1, y2, z1, line);
            line(x2, y1, z1, x2, y2, z1, line);
            line(x2, y1, z2, x2, y2, z2, line);
            line(x1, y1, z2, x1, y2, z2, line);
        }
    }

    public void flush(WorldRenderContext context) {
        if (lines.isEmpty() && quads.isEmpty()) return;
        Matrix4f m = Render3D.push(context);
        Render3D.begin();
        RenderSystem.disableCull();
        if (!quads.isEmpty()) {
            BufferBuilder b = Render3D.quads();
            for (Quad q : quads) {
                int a = (q.argb >> 24) & 255, r = (q.argb >> 16) & 255, g = (q.argb >> 8) & 255, bl = q.argb & 255;
                for (int i = 0; i < 4; i++) {
                    b.vertex(m, (float) q.p[i * 3], (float) q.p[i * 3 + 1], (float) q.p[i * 3 + 2]).color(r, g, bl, a);
                }
            }
            Render3D.flush(b);
        }
        if (!lines.isEmpty()) {
            BufferBuilder b = Render3D.lines();
            for (Line l : lines) {
                Render3D.line(b, m, new Vec3d(l.x1, l.y1, l.z1), new Vec3d(l.x2, l.y2, l.z2), l.argb);
            }
            Render3D.flush(b);
        }
        RenderSystem.enableCull();
        Render3D.end();
        Render3D.pop(context);
        lines.clear();
        quads.clear();
    }
}
