package abcxyz.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Estimates how much damage an explosion does to an entity, the way vanilla does it: distance, how much of the
 * entity's box the blast can "see" (exposure rays), armor and resistance. Blast protection is not counted, so the
 * result is on the safe side.
 */
public final class Explosions {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** Power of a respawn anchor explosion. */
    public static final float ANCHOR_POWER = 5.0f;
    /** Power of an end crystal explosion. */
    public static final float CRYSTAL_POWER = 6.0f;

    private Explosions() {
    }

    /** True when something solid sits between the point and the blast. */
    private static boolean blocked(Vec3d from, Vec3d source, Entity entity, BlockPos ignore, List<Box> extra) {
        Vec3d end = source;
        if (ignore != null) {
            // the respawn anchor is removed right before it explodes, so the last part of the ray (inside it) doesn't count
            Vec3d dir = from.subtract(source);
            double len = dir.length();
            if (len < 1.0e-4) return false;
            end = source.add(dir.multiply(Math.min(0.87, len) / len));
        }
        for (Box box : extra) {
            if (box.contains(from) || box.raycast(from, end).isPresent()) return true;
        }
        HitResult hit = mc.world.raycast(new RaycastContext(from, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
        return hit.getType() != HitResult.Type.MISS;
    }

    /**
     * Share of the entity's box that the blast reaches: 0 = fully covered, 1 = fully open.
     *
     * @param ignore a block that is gone when the blast happens (the anchor itself), or null
     * @param extra  blocks that are not placed yet but planned (their boxes), may be empty
     */
    public static float exposure(Vec3d source, Entity entity, BlockPos ignore, List<Box> extra) {
        Box box = entity.getBoundingBox();
        double dx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double dy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double dz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        if (dx < 0 || dy < 0 || dz < 0) return 0f;
        double offX = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0;
        double offZ = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;
        int seen = 0, total = 0;
        for (double x = 0; x <= 1.0; x += dx) {
            for (double y = 0; y <= 1.0; y += dy) {
                for (double z = 0; z <= 1.0; z += dz) {
                    Vec3d p = new Vec3d(
                            MathHelper.lerp(x, box.minX, box.maxX) + offX,
                            MathHelper.lerp(y, box.minY, box.maxY),
                            MathHelper.lerp(z, box.minZ, box.maxZ) + offZ);
                    if (!blocked(p, source, entity, ignore, extra)) seen++;
                    total++;
                }
            }
        }
        return total == 0 ? 0f : (float) seen / total;
    }

    /**
     * Damage the explosion at {@code source} would deal to the entity.
     *
     * @param power  ANCHOR_POWER or CRYSTAL_POWER
     * @param ignore a block that is gone when the blast happens (the anchor itself), or null
     * @param extra  blocks that are planned but not placed yet, may be empty
     */
    public static float damage(Vec3d source, float power, LivingEntity entity, BlockPos ignore, List<Box> extra) {
        float q = power * 2.0f;
        double d = entity.getPos().distanceTo(source) / q;
        if (d > 1.0) return 0f;
        double impact = (1.0 - d) * exposure(source, entity, ignore, extra);
        float dmg = (float) ((int) ((impact * impact + impact) / 2.0 * 7.0 * q + 1.0));

        if (entity instanceof PlayerEntity) {
            Difficulty difficulty = mc.world.getDifficulty();
            if (difficulty == Difficulty.EASY) dmg = Math.min(dmg / 2.0f + 1.0f, dmg);
            else if (difficulty == Difficulty.HARD) dmg = dmg * 3.0f / 2.0f;
        }

        float armor = entity.getArmor();
        float toughness = (float) entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float f = 2.0f + toughness / 4.0f;
        float g = MathHelper.clamp(armor - dmg / f, armor * 0.2f, 20.0f);
        dmg *= 1.0f - g / 25.0f;

        if (entity.hasStatusEffect(StatusEffects.RESISTANCE)) {
            int amp = entity.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier();
            dmg *= Math.max(0f, 1.0f - (amp + 1) * 0.2f);
        }
        return Math.max(dmg, 0f);
    }
}
