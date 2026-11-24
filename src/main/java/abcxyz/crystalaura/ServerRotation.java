package abcxyz.crystalaura;

import abcxyz.core.SilentRotation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * What the server believes you look at. request() asks for a rotation: it is sent in the movement packet of the next
 * tick (your own camera is not touched), limited to a human-like step per tick. Without a request the real rotation goes
 * out again. sent() is the rotation the server has right now.
 */
public final class ServerRotation {
    private static final double MAX_STEP = 25.0;

    private static Rotation requested;
    private static float requestedSpeed;
    private static float lastSpeed = 4.5f;
    private static Rotation server;
    private static ClientPlayerEntity owner;

    private ServerRotation() {
    }

    public static void request(Rotation rotation) {
        request(rotation, 0.0f);
    }

    public static void request(Rotation rotation, float speed) {
        if (requested == null) {
            requested = rotation;
            requestedSpeed = speed;
            if (speed > 0.0f) {
                lastSpeed = speed;
            }
        }
    }

    public static boolean pending() {
        return requested != null;
    }

    public static Rotation sent() {
        return server;
    }

    public static boolean canRotate(ClientPlayerEntity player) {
        return !player.isGliding() && !player.isUsingRiptide() && !player.hasVehicle() && !player.isSwimming();
    }

    /** The rotation the server would get next tick if this one were requested now. */
    public static Rotation next(ClientPlayerEntity player, Rotation wanted) {
        Rotation base = server != null ? server : new Rotation(player.getYaw(), player.getPitch());
        return step(base, wanted, lastSpeed);
    }

    /** Called right before the movement packet of every tick (ClientPlayerEntityMixin). */
    public static void beforeMovement(ClientPlayerEntity player) {
        if (player != owner) {
            owner = player;
            server = null;
            requested = null;
        }
        Rotation target = requested;
        requested = null;
        if (target != null && canRotate(player)) {
            Rotation base = server != null ? server : new Rotation(player.getYaw(), player.getPitch());
            Rotation next = step(base, target, requestedSpeed);
            SilentRotation.set(next.yaw(), next.pitch());
            server = next;
        } else {
            server = new Rotation(player.getYaw(), player.getPitch());
        }
    }

    private static Rotation step(Rotation base, Rotation wanted, float speed) {
        double yaw = MathHelper.wrapDegrees(wanted.yaw() - base.yaw());
        double pitch = wanted.pitch() - base.pitch();
        double distance = Math.hypot(yaw, pitch);
        if (distance < 1.0E-4) {
            return new Rotation(MathHelper.wrapDegrees(wanted.yaw()), wanted.pitch());
        }
        double scale = 1.0;
        if (speed > 0.0f) {
            double stride = Math.min(distance, 0.313 * distance + 1.56 * (double)speed);
            if (stride < distance) {
                stride *= 0.85 + 0.15 * ThreadLocalRandom.current().nextDouble();
            }
            scale = stride / distance;
        }
        double bigger = Math.max(Math.abs(yaw), Math.abs(pitch)) * scale;
        if (bigger > MAX_STEP) {
            scale *= MAX_STEP / bigger;
        }
        float newYaw = MathHelper.wrapDegrees(base.yaw() + (float)(yaw * scale));
        float newPitch = MathHelper.clamp(base.pitch() + (float)(pitch * scale), -90.0f, 90.0f);
        return new Rotation(newYaw, newPitch);
    }
}
