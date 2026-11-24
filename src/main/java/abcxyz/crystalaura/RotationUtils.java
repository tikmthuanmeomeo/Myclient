/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.crystalaura;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;

public final class RotationUtils {
    private RotationUtils() {
    }

    public static Rotation fromTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(MathHelper.atan2((double)dz, (double)dx) * 57.2957763671875) - 90.0f;
        float pitch = (float)(-(MathHelper.atan2((double)dy, (double)horizontal) * 57.2957763671875));
        return new Rotation(MathHelper.wrapDegrees((float)yaw), MathHelper.clamp((float)pitch, (float)-90.0f, (float)90.0f));
    }

    public static Rotation entityRotation(Entity entity) {
        return new Rotation(entity.getYaw(), entity.getPitch());
    }

    public static float yawDelta(float fromYaw, float toYaw) {
        return MathHelper.wrapDegrees((float)(toYaw - fromYaw));
    }

    public static float pitchDelta(float fromPitch, float toPitch) {
        return toPitch - fromPitch;
    }

    public static double angleTo(Rotation from, Rotation to) {
        float yaw = RotationUtils.yawDelta(from.yaw(), to.yaw());
        float pitch = RotationUtils.pitchDelta(from.pitch(), to.pitch());
        return Math.hypot(yaw, pitch);
    }

    public static double mouseScale(MinecraftClient mc, ClientPlayerEntity player) {
        double speed = (Double)mc.options.getMouseSensitivity().getValue() * (double)0.6f + (double)0.2f;
        return speed * speed * speed * (mc.options.getPerspective().isFirstPerson() && player.isUsingSpyglass() ? 1.0 : 8.0);
    }
}

