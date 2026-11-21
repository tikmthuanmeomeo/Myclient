package abcxyz.module.render;

import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The camera leaves your body and flies around (WASD, jump = up, sneak = down) while the player stays where it is.
 * The mouse turns the camera, not the player. The camera is moved in CameraMixin, the mouse in EntityMixin and the
 * player's own movement keys are cut in KeyboardInputMixin.
 */
public class Freecam extends Module {
    private final NumSetting speed = add(new NumSetting("Speed", 1.0, 0.1, 5.0, 0.1));

    private boolean ready = false;
    private ClientWorld world = null;
    private double x, y, z, prevX, prevY, prevZ;
    private float yaw, pitch, prevYaw, prevPitch;

    public Freecam() {
        super("Freecam", "Fly the camera around while your player stays put", Category.Render);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            ready = false;
            return;
        }
        Vec3d eye = mc.player.getEyePos();
        x = prevX = eye.x;
        y = prevY = eye.y;
        z = prevZ = eye.z;
        yaw = prevYaw = mc.player.getYaw();
        pitch = prevPitch = mc.player.getPitch();
        world = mc.world;
        ready = true;
    }

    @Override
    public void onDisable() {
        ready = false;
        world = null;
    }

    /** True while the free camera is in use. */
    public boolean isActive() {
        return isEnabled() && ready && mc.world != null && mc.world == world;
    }

    @Override
    public void onTick() {
        if (ready && mc.world != world) {
            setEnabled(false, true); // another world: the old camera spot means nothing
            return;
        }
        if (!isActive()) return;
        prevX = x;
        prevY = y;
        prevZ = z;
        prevYaw = yaw;
        prevPitch = pitch;
        if (mc.currentScreen != null) return;

        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double sideways = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        double up = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);

        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        double dx = forward * -sin + sideways * cos;
        double dz = forward * cos + sideways * sin;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 1.0e-6) {
            dx /= len;
            dz /= len;
        }
        double step = speed.d() * 0.5; // blocks per tick
        x += dx * step;
        z += dz * step;
        y += up * step;
    }

    /** Mouse movement turns the camera. */
    public void look(double cursorDeltaX, double cursorDeltaY) {
        yaw += (float) cursorDeltaX * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) cursorDeltaY * 0.15f, -90.0f, 90.0f);
    }

    public double getX(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevX, x);
    }

    public double getY(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevY, y);
    }

    public double getZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevZ, z);
    }

    public float getYaw(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevYaw, yaw);
    }

    public float getPitch(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevPitch, pitch);
    }
}
