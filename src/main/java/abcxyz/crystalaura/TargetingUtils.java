/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.crystalaura;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;

public final class TargetingUtils {
    private TargetingUtils() {
    }

    public static Vec3d closestPoint(Vec3d point, Box box) {
        return new Vec3d(MathHelper.clamp((double)point.x, (double)box.minX, (double)box.maxX), MathHelper.clamp((double)point.y, (double)box.minY, (double)box.maxY), MathHelper.clamp((double)point.z, (double)box.minZ, (double)box.maxZ));
    }
}

