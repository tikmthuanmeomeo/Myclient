package abcxyz.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Remembers the camera matrices of the last world render so world positions can be turned into screen positions. */
public final class Projection {
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static Vec3d camera = Vec3d.ZERO;
    private static float tickDelta = 1.0f;

    private Projection() {
    }

    public static void update(WorldRenderContext context) {
        PROJECTION.set(context.projectionMatrix());
        VIEW.set(context.matrixStack().peek().getPositionMatrix());
        camera = context.camera().getPos();
        tickDelta = context.tickCounter().getTickDelta(true);
    }

    public static float tickDelta() {
        return tickDelta;
    }

    /** @return {x, y} in scaled GUI coordinates, or null when the point is behind the camera or off screen. */
    public static double[] toScreen(Vec3d world) {
        Vector4f v = new Vector4f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z), 1.0f);
        VIEW.transform(v);
        PROJECTION.transform(v);
        if (v.w <= 0.01f) return null;
        float nx = v.x / v.w;
        float ny = v.y / v.w;
        if (Math.abs(nx) > 1.3f || Math.abs(ny) > 1.3f) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        double sx = (nx * 0.5 + 0.5) * mc.getWindow().getScaledWidth();
        double sy = (0.5 - ny * 0.5) * mc.getWindow().getScaledHeight();
        return new double[]{sx, sy};
    }
}
