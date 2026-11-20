package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Explosions;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Crosshair end crystals, always on while the module is enabled:
 * - you look at obsidian/bedrock (crystal in a hand): it places a crystal on it,
 * - you look at a crystal: it breaks it.
 * Place and Break have their own Range and Delay (in ms). It never places or breaks a crystal whose explosion would
 * deal more than "Max self damage" to you (estimated like vanilla: distance, exposure rays, armor, resistance), and
 * never one that would leave you at 1 health or less.
 * Nothing is rotated: it only acts on what your crosshair is already on.
 */
public class AutoCrystal extends Module {
    private final NumSetting placeRange = add(new NumSetting("PlaceRange", 4.5, 1, 6, 0.1));
    private final NumSetting placeDelay = add(new NumSetting("PlaceDelay", 50, 0, 500, 5));
    private final BoolSetting place = add(new BoolSetting("Place", true)).with(placeRange, placeDelay);

    private final NumSetting breakRange = add(new NumSetting("BreakRange", 4.5, 1, 6, 0.1));
    private final NumSetting breakDelay = add(new NumSetting("BreakDelay", 50, 0, 500, 5));
    private final BoolSetting breakCrystals = add(new BoolSetting("Break", true)).with(breakRange, breakDelay);

    private final NumSetting maxSelf = add(new NumSetting("MaxSelfDamage", 8.0, 0, 36, 0.5));
    private final BoolSetting autoSwitch = add(new BoolSetting("AutoSwitch", false));

    private long lastPlace = 0L;
    private long lastBreak = 0L;
    private int previousSlot = -1;

    /** Crystals we already hit (id -> time), so one crystal is not hit again before the server removes it. */
    private final Map<Integer, Long> attacked = new HashMap<>();
    private BlockPos lastPlacedBase = null;
    private long lastPlacedTime = 0L;

    public AutoCrystal() {
        super("AutoCrystal", "Look at obsidian/bedrock to place a crystal, look at a crystal to break it", Category.Combat);
        placeRange.label = "Range";
        placeDelay.label = "Delay (ms)";
        breakRange.label = "Range";
        breakDelay.label = "Delay (ms)";
        maxSelf.label = "Max self damage";
        autoSwitch.label = "Auto switch";
    }

    @Override
    public String info() {
        return placeDelay.i() + "/" + breakDelay.i();
    }

    @Override
    public void onDisable() {
        if (previousSlot != -1 && mc.player != null) mc.player.getInventory().selectedSlot = previousSlot;
        previousSlot = -1;
        attacked.clear();
        lastPlacedBase = null;
    }

    private boolean tooDangerous(float self) {
        return self > maxSelf.d() || self >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1.0f;
    }

    /** The crystal your crosshair is on within maxDist (nearest one), or null. */
    private EndCrystalEntity crystalUnderCrosshair(Vec3d eye, Vec3d look, double maxDist) {
        Vec3d end = eye.add(look.multiply(maxDist));
        EndCrystalEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Box search = new Box(eye, end).expand(1.0);
        for (Entity e : mc.world.getOtherEntities(mc.player, search, en -> en instanceof EndCrystalEntity && en.isAlive())) {
            Optional<Vec3d> hit = e.getBoundingBox().expand(e.getTargetingMargin()).raycast(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceTo(hit.get());
            if (d < bestDist) {
                bestDist = d;
                best = (EndCrystalEntity) e;
            }
        }
        return best;
    }

    @Override
    public void onFrame() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (!mc.player.isAlive() || mc.player.isSpectator()) return;
        if (!place.on() && !breakCrystals.on()) return;

        long now = System.currentTimeMillis();
        double reach = Math.max(place.on() ? placeRange.d() : 0.0, breakCrystals.on() ? breakRange.d() : 0.0);
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);

        // the block under the crosshair decides how far a crystal behind it could be hit
        HitResult blockRay = mc.player.raycast(reach, 1.0f, false);
        boolean blockHit = blockRay.getType() == HitResult.Type.BLOCK;
        double blockDist = blockHit ? blockRay.getPos().distanceTo(eye) : Double.MAX_VALUE;

        // 1) a crystal under the crosshair: break it
        if (breakCrystals.on()) {
            EndCrystalEntity crystal = crystalUnderCrosshair(eye, look, Math.min(breakRange.d(), blockDist));
            if (crystal != null) {
                breakCrystal(crystal, now);
                return;
            }
        }

        // 2) obsidian/bedrock under the crosshair: place a crystal on it
        if (place.on() && blockHit && blockDist <= placeRange.d()) {
            placeCrystal((BlockHitResult) blockRay, now);
        }
    }

    private void breakCrystal(EndCrystalEntity crystal, long now) {
        if (now - lastBreak < breakDelay.d()) return;
        attacked.values().removeIf(t -> now - t > 1000);
        Long hitAt = attacked.get(crystal.getId());
        if (hitAt != null && now - hitAt < 100) return; // already hit, the server has not removed it yet
        float self = Explosions.damage(crystal.getPos(), Explosions.CRYSTAL_POWER, mc.player, null, List.of());
        if (tooDangerous(self)) return;

        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastBreak = now;
        attacked.put(crystal.getId(), now);
    }

    private void placeCrystal(BlockHitResult hit, long now) {
        if (now - lastPlace < placeDelay.d()) return;
        BlockPos base = hit.getBlockPos();
        var state = mc.world.getBlockState(base);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) return;
        BlockPos above = base.up();
        if (!mc.world.getBlockState(above).isAir()) return;
        if (base.equals(lastPlacedBase) && now - lastPlacedTime < 250) return; // the crystal is on its way
        Box box = new Box(above.getX(), above.getY(), above.getZ(), above.getX() + 1, above.getY() + 2, above.getZ() + 1);
        if (!mc.world.getOtherEntities(null, box).isEmpty()) return;

        Hand hand = null;
        int crystalSlot = -1;
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) hand = Hand.OFF_HAND;
        else if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) hand = Hand.MAIN_HAND;
        else if (autoSwitch.on()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) {
                    crystalSlot = i;
                    break;
                }
            }
        }
        if (hand == null && crystalSlot == -1) return;

        Vec3d crystalPos = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        float self = Explosions.damage(crystalPos, Explosions.CRYSTAL_POWER, mc.player, null, List.of());
        if (tooDangerous(self)) return;

        lastPlace = now;
        if (hand == null) {
            if (previousSlot == -1) previousSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = crystalSlot;
            hand = Hand.MAIN_HAND;
        }
        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, hit);
        if (result.isAccepted()) {
            mc.player.swingHand(hand);
            lastPlacedBase = base.toImmutable();
            lastPlacedTime = now;
        }
    }
}
