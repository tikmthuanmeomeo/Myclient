package abcxyz.compat;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** One frame of world drawing for a ported module: draw with renderer, then call flush(). */
public class Render3DEvent {
    public final Renderer3D renderer = new Renderer3D();
    /** A point just in front of the camera (where tracer lines start). */
    public final Vec3d center;
    private final WorldRenderContext context;

    public Render3DEvent(WorldRenderContext context) {
        this.context = context;
        Camera camera = context.camera();
        this.center = camera.getPos().add(Vec3d.fromPolar(camera.getPitch(), camera.getYaw()));
    }

    public void flush() {
        renderer.flush(context);
    }
}
