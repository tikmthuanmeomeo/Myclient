package abcxyz.core;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Silent rotation: the server sees you looking at a point, your own camera does not move.
 * lookAt() only stores the wanted rotation; ClientPlayerEntityMixin applies it while the movement packet of the
 * tick is built (so no extra packets are sent) and puts your real rotation back right after.
 */
public final class SilentRotation {
    private static boolean pending = false;
    private static boolean applied = false;
    private static float yaw, pitch;
    private static float realYaw, realPitch;

    private SilentRotation() {
    }

    public static void lookAt(Vec3d eye, Vec3d target) {
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        yaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        pitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, flat)), -90f, 90f);
        pending = true;
    }

    /** Sets the rotation that the next movement packet carries. */
    public static void set(float newYaw, float newPitch) {
        yaw = newYaw;
        pitch = newPitch;
        pending = true;
    }

    /** Called before the movement packet is built. */
    public static void apply(ClientPlayerEntity player) {
        if (!pending) return;
        pending = false;
        realYaw = player.getYaw();
        realPitch = player.getPitch();
        player.setYaw(yaw);
        player.setPitch(pitch);
        applied = true;
    }

    /** Called after the movement packet is built. */
    public static void restore(ClientPlayerEntity player) {
        if (!applied) return;
        applied = false;
        player.setYaw(realYaw);
        player.setPitch(realPitch);
    }
}
