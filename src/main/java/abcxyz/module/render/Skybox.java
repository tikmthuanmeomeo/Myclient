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
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Makes the sky look like space: a black dome, stars (with a Milky Way band) and a ringed planet (Saturn)
 * in the south-west, a bit above the horizon. Drawn after the world, behind everything that is closer than the dome,
 * so the terrain always covers it. Not drawn in dimensions with a ceiling (Nether).
 */
public class Skybox extends Module {
    private final NumSetting stars = add(new NumSetting("Stars", 1800, 200, 4000, 100));
    private final NumSetting saturnSize = add(new NumSetting("SaturnSize", 6.0, 3.0, 15.0, 0.5));
    private final BoolSetting saturn = add(new BoolSetting("Saturn", true)).with(saturnSize);

    // star data: direction (x,y,z), angular size, twinkle phase, color
    private float[] dir = new float[0];
    private float[] size = new float[0];
    private float[] phase = new float[0];
    private int[] color = new int[0];
    private int built = -1;

    private static final int[] STAR_COLORS = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFBFD4FF, 0xFFA8C4FF, 0xFFFFF0C8, 0xFFFFD9B0};
    private static final int[] BANDS = {0xFFE8D3A0, 0xFFD8BC84, 0xFFEBD9B0, 0xFFC9A872, 0xFFE0C58F, 0xFFB99B66, 0xFFDCC08A, 0xFFCFB27C, 0xFFE6D09C};

    public Skybox() {
        super("Skybox", "Space sky with stars and Saturn in the south-west", Category.Render);
        saturnSize.label = "Size";
    }

    private void generate(int count) {
        Random rnd = new Random(20261118L);
        dir = new float[count * 3];
        size = new float[count];
        phase = new float[count];
        color = new int[count];
        // Milky Way: a band around a great circle
        Vector3f n = new Vector3f(0.35f, 1f, 0.45f).normalize();
        Vector3f u = new Vector3f(1f, 0f, 0f).sub(new Vector3f(n).mul(n.x)).normalize();
        Vector3f v = new Vector3f(n).cross(u).normalize();
        for (int i = 0; i < count; i++) {
            Vector3f d = new Vector3f();
            if (rnd.nextFloat() < 0.35f) {
                float a = rnd.nextFloat() * (float) (Math.PI * 2);
                float off = (float) (rnd.nextGaussian() * 0.14);
                d.set(u).mul((float) Math.cos(a)).add(new Vector3f(v).mul((float) Math.sin(a))).add(new Vector3f(n).mul(off)).normalize();
            } else {
                float z = rnd.nextFloat() * 2f - 1f;
                float a = rnd.nextFloat() * (float) (Math.PI * 2);
                float r = (float) Math.sqrt(1f - z * z);
                d.set(r * (float) Math.cos(a), z, r * (float) Math.sin(a));
            }
            dir[i * 3] = d.x;
            dir[i * 3 + 1] = d.y;
            dir[i * 3 + 2] = d.z;
            float r = rnd.nextFloat();
            size[i] = 0.0012f + r * r * r * 0.0040f;
            phase[i] = rnd.nextFloat() * (float) (Math.PI * 2);
            color[i] = STAR_COLORS[rnd.nextInt(STAR_COLORS.length)];
        }
        built = count;
    }

    private static void vtx(BufferBuilder b, Matrix4f m, double x, double y, double z, int argb) {
        b.vertex(m, (float) x, (float) y, (float) z).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int shade(int argb, float s, int alpha) {
        int r = Math.min(255, (int) (((argb >> 16) & 255) * s));
        int g = Math.min(255, (int) (((argb >> 8) & 255) * s));
        int bl = Math.min(255, (int) ((argb & 255) * s));
        return (alpha << 24) | (r << 16) | (g << 8) | bl;
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        if (mc.world.getDimension().hasCeiling()) return;
        int count = stars.i();
        if (count != built) generate(count);

        float radius = Math.max(64f, Math.min(256f, mc.options.getViewDistance().getValue() * 16f * 0.9f));
        Matrix4f m = new Matrix4f(context.matrixStack().peek().getPositionMatrix());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        drawDome(b, m, radius);
        drawStars(b, m, radius - 1.5f);
        if (saturn.on()) drawSaturn(b, m, radius - 3.0f);

        BuiltBuffer builtBuffer = b.endNullable();
        if (builtBuffer != null) BufferRenderer.drawWithGlobalProgram(builtBuffer);
        if (saturn.on()) drawHalo(m, radius - 3.0f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawDome(BufferBuilder b, Matrix4f m, float r) {
        final int lat = 12, lon = 24;
        int horizon = 0xFF0A1024, zenith = 0xFF020309;
        for (int i = 0; i < lat; i++) {
            double a0 = Math.PI * (-0.5 + (double) i / lat), a1 = Math.PI * (-0.5 + (double) (i + 1) / lat);
            int c0 = lerpColor(horizon, zenith, (float) Math.abs(Math.sin(a0)));
            int c1 = lerpColor(horizon, zenith, (float) Math.abs(Math.sin(a1)));
            for (int j = 0; j < lon; j++) {
                double t0 = Math.PI * 2 * j / lon, t1 = Math.PI * 2 * (j + 1) / lon;
                vtx(b, m, r * Math.cos(a0) * Math.cos(t0), r * Math.sin(a0), r * Math.cos(a0) * Math.sin(t0), c0);
                vtx(b, m, r * Math.cos(a1) * Math.cos(t0), r * Math.sin(a1), r * Math.cos(a1) * Math.sin(t0), c1);
                vtx(b, m, r * Math.cos(a1) * Math.cos(t1), r * Math.sin(a1), r * Math.cos(a1) * Math.sin(t1), c1);
                vtx(b, m, r * Math.cos(a0) * Math.cos(t1), r * Math.sin(a0), r * Math.cos(a0) * Math.sin(t1), c0);
            }
        }
    }

    private void drawStars(BufferBuilder b, Matrix4f m, float r) {
        double time = (System.currentTimeMillis() % 200000L) / 1000.0;
        Vector3f d = new Vector3f(), t1 = new Vector3f(), t2 = new Vector3f();
        for (int i = 0; i < built; i++) {
            d.set(dir[i * 3], dir[i * 3 + 1], dir[i * 3 + 2]);
            // two vectors perpendicular to the direction, so the star is a small square facing the camera
            t1.set(0f, 1f, 0f).cross(d);
            if (t1.lengthSquared() < 1.0e-4f) t1.set(1f, 0f, 0f).cross(d);
            t1.normalize();
            t2.set(d).cross(t1).normalize();
            float s = size[i] * r;
            float twinkle = 0.65f + 0.35f * (float) Math.sin(time * (1.5 + phase[i] * 0.3) + phase[i] * 7.0);
            int alpha = (int) (255 * Math.min(1f, twinkle + (size[i] - 0.0012f) * 60f));
            int c = (alpha << 24) | (color[i] & 0xFFFFFF);
            double cx = d.x * r, cy = d.y * r, cz = d.z * r;
            vtx(b, m, cx - t1.x * s - t2.x * s, cy - t1.y * s - t2.y * s, cz - t1.z * s - t2.z * s, c);
            vtx(b, m, cx + t1.x * s - t2.x * s, cy + t1.y * s - t2.y * s, cz + t1.z * s - t2.z * s, c);
            vtx(b, m, cx + t1.x * s + t2.x * s, cy + t1.y * s + t2.y * s, cz + t1.z * s + t2.z * s, c);
            vtx(b, m, cx - t1.x * s + t2.x * s, cy - t1.y * s + t2.y * s, cz - t1.z * s + t2.z * s, c);
        }
    }

    // ---------------------------------------------------------------- Saturn
    // Geometry and colors are built once, in units of the planet radius, and only moved/scaled each frame.

    private float[] pPos, rPos;
    private int[] pCol, rCol;
    private final Vector3f toPlanet = new Vector3f();
    private int pIndex = 0;

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    private static float hash(int n) {
        int x = n * 374761393 + 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        x ^= x >>> 16;
        return (x & 0xFFFFFF) / 16777215f;
    }

    private static float vnoise(float x) {
        int i = (int) Math.floor(x);
        float f = x - i;
        f = f * f * (3f - 2f * f);
        return hash(i) * (1f - f) + hash(i + 1) * f;
    }

    /** Cloud band color for a value 0..1 (dark tan to pale cream). */
    private static int palette(float f) {
        int[] stops = {0xA98A5C, 0xCBB07A, 0xE4D2A2, 0xF1E5C4};
        float x = Math.max(0f, Math.min(0.9999f, f)) * (stops.length - 1);
        int i = (int) x;
        return lerpColor(stops[i], stops[i + 1], x - i);
    }

    /** How solid the rings are at a distance r (in planet radii): C ring, B ring, Cassini gap, A ring, Encke gap. */
    private static float ringDensity(float r) {
        float fine = 0.75f + 0.25f * vnoise(r * 70f) + 0.1f * (float) Math.sin(r * 160f);
        float d;
        if (r < 1.22f || r > 2.36f) return 0f;
        if (r < 1.52f) d = 0.16f + 0.1f * vnoise(r * 30f);
        else if (r < 1.95f) d = 0.72f + 0.22f * smoothstep(1.52f, 1.8f, r);
        else if (r < 2.03f) d = 0.05f;
        else if (r < 2.26f) d = 0.5f;
        else if (r < 2.29f) d = 0.08f;
        else d = 0.32f;
        float edge = smoothstep(1.22f, 1.3f, r) * (1f - smoothstep(2.3f, 2.36f, r));
        return Math.max(0f, Math.min(1f, d * fine * edge));
    }

    private static int ringColor(float r) {
        if (r < 1.52f) return 0xA89A7E;
        if (r < 1.95f) return lerpColor(0xCDB88C, 0xEBDDB4, (r - 1.52f) / 0.43f);
        return 0xD8C69C;
    }

    private void buildSaturn() {
        double az = Math.toRadians(45.0), el = Math.toRadians(28.0);
        toPlanet.set((float) (-Math.sin(az) * Math.cos(el)), (float) Math.sin(el), (float) (Math.cos(az) * Math.cos(el)));
        Vector3f up = new Vector3f(0f, 1f, 0f);
        float theta = (float) Math.toRadians(20.0);
        // the planet's axis, which is also the normal of the ring plane: "up" turned a bit around the view axis
        Vector3f n = new Vector3f(up).mul((float) Math.cos(theta))
                .add(new Vector3f(toPlanet).cross(up).mul((float) Math.sin(theta)))
                .add(new Vector3f(toPlanet).mul(toPlanet.dot(up) * (1f - (float) Math.cos(theta)))).normalize();
        Vector3f u = new Vector3f(n).cross(toPlanet).normalize();
        Vector3f v = new Vector3f(n).cross(u).normalize();
        Vector3f w = new Vector3f(up).cross(toPlanet).normalize();
        Vector3f light = new Vector3f(toPlanet).mul(-0.55f).add(new Vector3f(w).mul(0.8f)).add(0f, 0.3f, 0f).normalize();

        final int lat = 72, lon = 96;
        pPos = new float[lat * lon * 4 * 3];
        pCol = new int[lat * lon * 4];
        pIndex = 0;
        for (int i = 0; i < lat; i++) {
            double a0 = Math.PI * (-0.5 + (double) i / lat), a1 = Math.PI * (-0.5 + (double) (i + 1) / lat);
            for (int j = 0; j < lon; j++) {
                double t0 = Math.PI * 2 * j / lon, t1 = Math.PI * 2 * (j + 1) / lon;
                planetVertex(a0, t0, u, v, n, light);
                planetVertex(a1, t0, u, v, n, light);
                planetVertex(a1, t1, u, v, n, light);
                planetVertex(a0, t1, u, v, n, light);
            }
        }

        final int radial = 40, ang = 120;
        rPos = new float[radial * ang * 4 * 3];
        rCol = new int[radial * ang * 4];
        int k = 0;
        for (int i = 0; i < radial; i++) {
            float r0 = 1.22f + (2.36f - 1.22f) * i / radial, r1 = 1.22f + (2.36f - 1.22f) * (i + 1) / radial;
            for (int j = 0; j < ang; j++) {
                double t0 = Math.PI * 2 * j / ang, t1 = Math.PI * 2 * (j + 1) / ang;
                float[][] corner = {{r0, (float) t0}, {r1, (float) t0}, {r1, (float) t1}, {r0, (float) t1}};
                for (float[] cv : corner) {
                    float cs = (float) Math.cos(cv[1]), sn = (float) Math.sin(cv[1]);
                    float x = (u.x * cs + v.x * sn) * cv[0], y = (u.y * cs + v.y * sn) * cv[0], z = (u.z * cs + v.z * sn) * cv[0];
                    rPos[k * 3] = x;
                    rPos[k * 3 + 1] = y;
                    rPos[k * 3 + 2] = z;
                    float mid = (r0 + r1) / 2f;
                    // the planet throws a shadow on the ring (soft edge)
                    float proj = -(x * light.x + y * light.y + z * light.z);
                    float shadow = 1f;
                    if (proj > 0f) {
                        float px = -x - light.x * proj, py = -y - light.y * proj, pz = -z - light.z * proj;
                        shadow = smoothstep(0.95f, 1.05f, (float) Math.sqrt(px * px + py * py + pz * pz));
                    }
                    float lit = 0.1f + 0.9f * shadow;
                    int alpha = (int) (ringDensity(mid) * 0.95f * 255f);
                    rCol[k++] = shade(ringColor(mid), lit, alpha);
                }
            }
        }
    }

    private void planetVertex(double a, double t, Vector3f u, Vector3f v, Vector3f n, Vector3f light) {
        float ca = (float) Math.cos(a), sa = (float) Math.sin(a), ct = (float) Math.cos(t), st = (float) Math.sin(t);
        float nx = u.x * ca * ct + v.x * ca * st + n.x * sa;
        float ny = u.y * ca * ct + v.y * ca * st + n.y * sa;
        float nz = u.z * ca * ct + v.z * ca * st + n.z * sa;
        // Saturn is slightly flattened at the poles
        float px = nx - n.x * sa * 0.1f, py = ny - n.y * sa * 0.1f, pz = nz - n.z * sa * 0.1f;
        pPos[pIndex * 3] = px;
        pPos[pIndex * 3 + 1] = py;
        pPos[pIndex * 3 + 2] = pz;

        // cloud bands with a little turbulence, greyer towards the poles
        float la = (float) a, lt = (float) t;
        float f = 0.5f + 0.5f * (0.5f * (float) Math.sin(la * 7 + 0.7) + 0.3f * (float) Math.sin(la * 17 + 2.1) + 0.2f * (float) Math.sin(la * 31 + 4.0));
        f += (vnoise(la * 45f + (float) Math.sin(lt * 3.0) * 2f) - 0.5f) * 0.16f;
        int base = palette(f);
        base = lerpColor(base, 0x9AA0A6, smoothstep(1.0f, 1.45f, Math.abs(la)) * 0.55f);

        // soft terminator, darker towards the edge of the disc, and the shadow of the rings on the planet
        float nl = nx * light.x + ny * light.y + nz * light.z;
        float term = smoothstep(-0.08f, 0.5f, nl);
        float mu = Math.max(0f, -(nx * toPlanet.x + ny * toPlanet.y + nz * toPlanet.z));
        float limb = 0.5f + 0.5f * (float) Math.sqrt(mu);
        float ringShadow = 0f;
        float denom = light.dot(n);
        if (nl > 0f && Math.abs(denom) > 1.0e-3f) {
            float t0 = -(px * n.x + py * n.y + pz * n.z) / denom;
            if (t0 > 0f) {
                float hx = px + light.x * t0, hy = py + light.y * t0, hz = pz + light.z * t0;
                ringShadow = ringDensity((float) Math.sqrt(hx * hx + hy * hy + hz * hz));
            }
        }
        float s = 0.04f + 0.96f * term * limb * (1f - 0.8f * ringShadow);
        pCol[pIndex++] = shade(base, s, 255);
    }

    private void drawSaturn(BufferBuilder b, Matrix4f m, float dist) {
        if (pPos == null) buildSaturn();
        float rp = (float) (Math.tan(Math.toRadians(saturnSize.d() / 2.0)) * dist);
        float cx = toPlanet.x * dist, cy = toPlanet.y * dist, cz = toPlanet.z * dist;
        for (int i = 0; i < pCol.length; i++) {
            vtx(b, m, cx + pPos[i * 3] * rp, cy + pPos[i * 3 + 1] * rp, cz + pPos[i * 3 + 2] * rp, pCol[i]);
        }
        for (int i = 0; i < rCol.length; i++) {
            vtx(b, m, cx + rPos[i * 3] * rp, cy + rPos[i * 3 + 1] * rp, cz + rPos[i * 3 + 2] * rp, rCol[i]);
        }
    }

    /** A faint glow just around the planet's edge. Drawn after everything else, without writing depth. */
    private void drawHalo(Matrix4f m, float dist) {
        float rp = (float) (Math.tan(Math.toRadians(saturnSize.d() / 2.0)) * dist);
        Vector3f c = new Vector3f(toPlanet).mul(dist);
        Vector3f p1 = new Vector3f(0f, 1f, 0f).cross(toPlanet).normalize();
        Vector3f p2 = new Vector3f(toPlanet).cross(p1).normalize();
        RenderSystem.depthMask(false);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        final int seg = 48;
        for (int j = 0; j < seg; j++) {
            double t0 = Math.PI * 2 * j / seg, t1 = Math.PI * 2 * (j + 1) / seg;
            double[][] pts = {{0.97, t0, 80}, {1.32, t0, 0}, {1.32, t1, 0}, {0.97, t1, 80}};
            for (double[] pt : pts) {
                double cs = Math.cos(pt[1]), sn = Math.sin(pt[1]);
                double x = c.x + (p1.x * cs + p2.x * sn) * pt[0] * rp;
                double y = c.y + (p1.y * cs + p2.y * sn) * pt[0] * rp;
                double z = c.z + (p1.z * cs + p2.z * sn) * pt[0] * rp;
                vtx(b, m, x, y, z, ((int) pt[2] << 24) | 0xF3DDA6);
            }
        }
        BuiltBuffer halo = b.endNullable();
        if (halo != null) BufferRenderer.drawWithGlobalProgram(halo);
        RenderSystem.depthMask(true);
    }
}
