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

    private void drawSaturn(BufferBuilder b, Matrix4f m, float dist) {
        // south-west = between south (+Z) and west (-X): yaw 45 degrees; 28 degrees above the horizon
        double az = Math.toRadians(45.0), el = Math.toRadians(28.0);
        Vector3f toPlanet = new Vector3f((float) (-Math.sin(az) * Math.cos(el)), (float) Math.sin(el), (float) (Math.cos(az) * Math.cos(el)));
        Vector3f c = new Vector3f(toPlanet).mul(dist);
        float rp = (float) (Math.tan(Math.toRadians(saturnSize.d() / 2.0)) * dist);
        Vector3f light = new Vector3f(0.8f, 0.5f, -0.25f).normalize();

        // planet: horizontal bands, lit from one side
        final int lat = 24, lon = 48;
        for (int i = 0; i < lat; i++) {
            double a0 = Math.PI * (-0.5 + (double) i / lat), a1 = Math.PI * (-0.5 + (double) (i + 1) / lat);
            int band = Math.min(BANDS.length - 1, (int) ((Math.sin((a0 + a1) / 2) * 0.5 + 0.5) * BANDS.length));
            for (int j = 0; j < lon; j++) {
                double t0 = Math.PI * 2 * j / lon, t1 = Math.PI * 2 * (j + 1) / lon;
                double[][] corners = {{a0, t0}, {a1, t0}, {a1, t1}, {a0, t1}};
                for (double[] k : corners) {
                    float nx = (float) (Math.cos(k[0]) * Math.cos(k[1])), ny = (float) Math.sin(k[0]), nz = (float) (Math.cos(k[0]) * Math.sin(k[1]));
                    float s = 0.22f + 0.78f * Math.max(0f, nx * light.x + ny * light.y + nz * light.z);
                    vtx(b, m, c.x + nx * rp, c.y + ny * rp, c.z + nz * rp, shade(BANDS[band], s, 255));
                }
            }
        }

        // ring: in a plane around the planet, tilted a bit on screen; the Cassini gap is left empty
        Vector3f up = new Vector3f(0f, 1f, 0f);
        float theta = (float) Math.toRadians(20.0);
        Vector3f n = new Vector3f(up).mul((float) Math.cos(theta))
                .add(new Vector3f(toPlanet).cross(up).mul((float) Math.sin(theta)))
                .add(new Vector3f(toPlanet).mul(toPlanet.dot(up) * (1f - (float) Math.cos(theta)))).normalize();
        Vector3f u = new Vector3f(n).cross(toPlanet).normalize();
        Vector3f v = new Vector3f(n).cross(u).normalize();
        float[][] rings = {{1.35f, 1.62f, 0xC8B48A, 150}, {1.62f, 1.96f, 0xE2CFA0, 225}, {2.06f, 2.42f, 0xD2BD90, 165}};
        final int seg = 96;
        for (float[] ring : rings) {
            int col = (int) ring[2];
            int alpha = (int) ring[3];
            for (int j = 0; j < seg; j++) {
                double t0 = Math.PI * 2 * j / seg, t1 = Math.PI * 2 * (j + 1) / seg;
                float ri = ring[0] * rp, ro = ring[1] * rp;
                double[][] pts = {{ri, t0}, {ro, t0}, {ro, t1}, {ri, t1}};
                for (double[] p : pts) {
                    double cs = Math.cos(p[1]), sn = Math.sin(p[1]);
                    double x = c.x + (u.x * cs + v.x * sn) * p[0];
                    double y = c.y + (u.y * cs + v.y * sn) * p[0];
                    double z = c.z + (u.z * cs + v.z * sn) * p[0];
                    vtx(b, m, x, y, z, (alpha << 24) | col);
                }
            }
        }
    }
}
