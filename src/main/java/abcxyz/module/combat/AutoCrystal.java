package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.ModeSetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Places End Crystals on obsidian/bedrock next to the nearest player and breaks them again.
 * Damage is estimated like the vanilla explosion (distance, exposure, armor, resistance), so it only places
 * and breaks crystals that hurt the target enough (MinDamage) and you not too much (MaxSelfDmg).
 * Settings: "Place" (right click: Range, Delay) and "Break" (right click: Range, Delay).
 */
public class AutoCrystal extends Module {
    private final NumSetting targetRange = add(new NumSetting("TargetRange", 10, 3, 16, 1));
    private final NumSetting minDamage = add(new NumSetting("MinDamage", 4.0, 1, 36, 0.5));
    private final NumSetting maxSelf = add(new NumSetting("MaxSelfDmg", 8.0, 0, 36, 0.5));

    private final NumSetting placeRange = add(new NumSetting("PlaceRange", 4.5, 1, 6, 0.1));
    private final NumSetting placeDelay = add(new NumSetting("PlaceDelay", 50, 0, 500, 10));
    private final BoolSetting place = add(new BoolSetting("Place", true)).with(placeRange, placeDelay);

    private final NumSetting breakRange = add(new NumSetting("BreakRange", 4.5, 1, 6, 0.1));
    private final NumSetting breakDelay = add(new NumSetting("BreakDelay", 50, 0, 500, 10));
    private final BoolSetting breakCrystals = add(new BoolSetting("Break", true)).with(breakRange, breakDelay);

    private final BoolSetting autoSwitch = add(new BoolSetting("AutoSwitch", true));
    private final BoolSetting antiSuicide = add(new BoolSetting("AntiSuicide", true));
    /** Off = no rotation, Packet = look at the target only on the server (restored right after), Client = turns your real view. */
    private final ModeSetting rotate = add(new ModeSetting("Rotate", "Packet", "Off", "Packet", "Client"));

    private long lastPlace = 0L;
    private long lastBreak = 0L;
    private int previousSlot = -1;

    public AutoCrystal() {
        super("AutoCrystal", "Places and breaks end crystals near the closest player", Category.Combat);
        placeRange.label = "Range";
        placeDelay.label = "Delay";
        breakRange.label = "Range";
        breakDelay.label = "Delay";
    }

    @Override
    public String info() {
        return String.valueOf(minDamage.d());
    }

    @Override
    public void onDisable() {
        if (previousSlot != -1 && mc.player != null) mc.player.getInventory().selectedSlot = previousSlot;
        previousSlot = -1;
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestDist = targetRange.d();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            double d = mc.player.distanceTo(p);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- damage estimation

    private float exposure(Vec3d source, Entity entity) {
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
                    HitResult hit = mc.world.raycast(new RaycastContext(p, source,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity));
                    if (hit.getType() == HitResult.Type.MISS) seen++;
                    total++;
                }
            }
        }
        return total == 0 ? 0f : (float) seen / total;
    }

    /** Damage without looking at walls: the most a crystal at this position could do. */
    private float upperBound(Vec3d source, LivingEntity entity) {
        double d = entity.getPos().distanceTo(source) / 12.0;
        if (d > 1.0) return 0f;
        double impact = 1.0 - d;
        return (float) ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0);
    }

    private float damage(Vec3d source, LivingEntity entity) {
        double d = entity.getPos().distanceTo(source) / 12.0;
        if (d > 1.0) return 0f;
        double impact = (1.0 - d) * exposure(source, entity);
        float dmg = (float) ((int) ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0));

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

    private boolean tooDangerous(float self) {
        if (self > maxSelf.d()) return true;
        return antiSuicide.on() && self >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1.0f;
    }

    // ---------------------------------------------------------------- rotation

    private boolean rotateTo(Vec3d target) {
        if (rotate.is("Off")) return false;
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = MathHelper.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        float pitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, flat)), -90f, 90f);
        if (rotate.is("Client")) {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
            return false;
        }
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                mc.player.isOnGround(), mc.player.horizontalCollision));
        return true;
    }

    /** After a packet rotation: tell the server where you are really looking again. */
    private void restoreRotation() {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), mc.player.getPitch(),
                mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    // ---------------------------------------------------------------- actions

    @Override
    public void onFrame() {
        if (mc.currentScreen != null || mc.interactionManager == null) return;
        PlayerEntity target = findTarget();
        if (target == null) return;
        long now = System.currentTimeMillis();

        if (breakCrystals.on() && now - lastBreak >= breakDelay.d()) {
            if (doBreak(target)) {
                lastBreak = now;
                return;
            }
        }
        if (place.on() && now - lastPlace >= placeDelay.d()) {
            lastPlace = now;
            doPlace(target);
        }
    }

    private boolean doBreak(PlayerEntity target) {
        EndCrystalEntity best = null;
        float bestScore = -Float.MAX_VALUE;
        double r2 = breakRange.d() * breakRange.d();
        Vec3d eye = mc.player.getEyePos();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity crystal) || !crystal.isAlive()) continue;
            if (eye.squaredDistanceTo(crystal.getBoundingBox().getCenter()) > r2) continue;
            Vec3d pos = crystal.getPos();
            float dmg = damage(pos, target);
            if (dmg < minDamage.d()) continue;
            float self = damage(pos, mc.player);
            if (tooDangerous(self)) continue;
            float score = dmg - self;
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }
        if (best == null) return false;
        boolean rotated = rotateTo(best.getBoundingBox().getCenter());
        mc.interactionManager.attackEntity(mc.player, best);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (rotated) restoreRotation();
        return true;
    }

    private record Candidate(BlockPos base, Vec3d crystalPos, float bound) {
    }

    private void doPlace(PlayerEntity target) {
        Hand hand = null;
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) hand = Hand.OFF_HAND;
        else if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) hand = Hand.MAIN_HAND;
        int crystalSlot = -1;
        if (hand == null) {
            if (!autoSwitch.on()) return;
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) {
                    crystalSlot = i;
                    break;
                }
            }
            if (crystalSlot == -1) return;
        }

        Vec3d eye = mc.player.getEyePos();
        double pr = placeRange.d();
        BlockPos center = target.getBlockPos();
        List<Candidate> candidates = new ArrayList<>();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos base = center.add(dx, dy, dz);
                    var state = mc.world.getBlockState(base);
                    if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) continue;
                    if (!mc.world.getBlockState(base.up()).isAir()) continue;
                    if (eye.distanceTo(Vec3d.ofCenter(base)) > pr) continue;
                    Vec3d crystalPos = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
                    float bound = upperBound(crystalPos, target);
                    if (bound < minDamage.d()) continue;
                    candidates.add(new Candidate(base, crystalPos, bound));
                }
            }
        }
        if (candidates.isEmpty()) return;
        candidates.sort(Comparator.comparingDouble((Candidate c) -> c.bound()).reversed());

        BlockPos bestBase = null;
        float bestScore = -Float.MAX_VALUE;
        int checked = 0;
        for (Candidate c : candidates) {
            if (checked++ >= 8) break;
            BlockPos above = c.base().up();
            Box box = new Box(above.getX(), above.getY(), above.getZ(), above.getX() + 1, above.getY() + 2, above.getZ() + 1);
            if (!mc.world.getOtherEntities(null, box).isEmpty()) continue;
            float dmg = damage(c.crystalPos(), target);
            if (dmg < minDamage.d()) continue;
            float self = damage(c.crystalPos(), mc.player);
            if (tooDangerous(self)) continue;
            float score = dmg - self;
            if (score > bestScore) {
                bestScore = score;
                bestBase = c.base();
            }
        }
        if (bestBase == null) return;

        if (hand == null) {
            if (previousSlot == -1) previousSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = crystalSlot;
            hand = Hand.MAIN_HAND;
        }
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(bestBase).add(0, 0.5, 0), Direction.UP, bestBase, false);
        boolean rotated = rotateTo(hit.getPos());
        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, hit);
        if (result.isAccepted()) mc.player.swingHand(hand);
        if (rotated) restoreRotation();
    }
}
