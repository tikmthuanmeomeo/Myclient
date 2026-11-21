package abcxyz.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Helpers for looking at a point: either turning the real camera or only telling the server (silent). */
public final class Rotations {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private Rotations() {
    }

    public static float yawTo(Vec3d target) {
        Vec3d d = target.subtract(mc.player.getEyePos());
        return (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
    }

    public static float pitchTo(Vec3d target) {
        Vec3d d = target.subtract(mc.player.getEyePos());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        return MathHelper.clamp((float) -Math.toDegrees(Math.atan2(d.y, flat)), -90f, 90f);
    }

    /**
     * Turns the real camera towards the point. The yaw is set to the nearest equivalent angle of the current one
     * (never a jump of 360 degrees), otherwise the camera interpolation spins the view for a moment.
     */
    public static void lookAt(Vec3d target) {
        float current = mc.player.getYaw();
        mc.player.setYaw(current + MathHelper.wrapDegrees(yawTo(target) - current));
        mc.player.setPitch(pitchTo(target));
    }

    public static boolean isLookingAt(Vec3d target, float tolerance) {
        float dYaw = MathHelper.wrapDegrees(yawTo(target) - mc.player.getYaw());
        float dPitch = pitchTo(target) - mc.player.getPitch();
        return Math.abs(dYaw) <= tolerance && Math.abs(dPitch) <= tolerance;
    }

    /** Sends the server a look towards the point without moving your camera. Call restore() after the action. */
    public static void silentLook(Vec3d target) {
        send(MathHelper.wrapDegrees(yawTo(target)), pitchTo(target));
    }

    /** Tells the server your real camera rotation again. */
    public static void restore() {
        send(mc.player.getYaw(), mc.player.getPitch());
    }

    private static void send(float yaw, float pitch) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }
}
