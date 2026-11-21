package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.ModeSetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Attacks automatically when your crosshair is on a target. It is checked every rendered frame (not only every 50 ms
 * tick), so a target that sweeps through the crosshair is not missed.
 * <p>
 * Hit mode: Always / Only crits / Crits + Sprint (a crit, or any hit while you sprint).
 * A crit does 1.5x damage; it needs a fall (not on the ground, falling, not sprinting). Fall tick = how many ticks you
 * must have been falling before a crit hit is allowed.
 * Delay mode: 1.9 waits for the attack cooldown (plus Delay ms), 1.8 has no cooldown and hits CPS times per second.
 * Miss chance: percent of the hits that only swing the arm (like a real miss). Reach: how far a target may be.
 */
public class TriggerBot extends Module {
    private final ModeSetting hitMode = add(new ModeSetting("HitMode", "Always", "Always", "OnlyCrits", "Crits+Sprint"));
    private final NumSetting fallTick = add(new NumSetting("FallTick", 1, 0, 10, 1));
    private final ModeSetting delayMode = add(new ModeSetting("DelayMode", "1.9", "1.9", "1.8"));
    private final NumSetting delay = add(new NumSetting("Delay", 0, 0, 500, 10));
    private final NumSetting cps = add(new NumSetting("CPS", 10, 1, 20, 1));
    private final NumSetting missChance = add(new NumSetting("MissChance", 0, 0, 100, 1));
    private final NumSetting reach = add(new NumSetting("Reach", 3.0, 1.0, 6.0, 0.1));
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting mobs = add(new BoolSetting("Mobs", false));

    private long lastAttack = 0L;
    /** Ticks in a row that the player has been in the air and falling. */
    private int fallTicks = 0;

    public TriggerBot() {
        super("TriggerBot", "Attacks when your crosshair is on a target", Category.Combat);
        hitMode.label = "Hit mode";
        fallTick.label = "Fall tick";
        delayMode.label = "Delay mode";
        delay.label = "Delay ms (1.9)";
        cps.label = "CPS (1.8)";
        missChance.label = "Miss chance %";
    }

    @Override
    public String info() {
        return hitMode.get() + " " + delayMode.get();
    }

    @Override
    public void onEnable() {
        fallTicks = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (!mc.player.isOnGround() && mc.player.fallDistance > 0.0f) fallTicks++;
        else fallTicks = 0;
    }

    /** Would a hit right now be a critical hit (1.5x damage), and have we been falling long enough? */
    private boolean canCrit() {
        PlayerEntity p = mc.player;
        return fallTicks >= fallTick.i() && p.fallDistance > 0.0f && !p.isOnGround() && !p.isClimbing()
                && !p.isTouchingWater() && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle()
                && !p.isSprinting();
    }

    private boolean hitModeAllows() {
        if (hitMode.is("Always")) return true;
        if (hitMode.is("OnlyCrits")) return canCrit();
        return canCrit() || mc.player.isSprinting(); // Crits+Sprint
    }

    /** The nearest living entity under the crosshair within Reach, with nothing solid in the way. */
    private LivingEntity findTarget() {
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(mc.player.getRotationVec(1.0f).multiply(reach.d()));
        Box search = new Box(eye, end).expand(1.0);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : mc.world.getOtherEntities(mc.player, search, en -> en instanceof LivingEntity && en.isAlive() && en.canHit())) {
            LivingEntity living = (LivingEntity) e;
            if (living.isSpectator()) continue;
            if (living instanceof PlayerEntity ? !players.on() : !mobs.on()) continue;
            Optional<Vec3d> hit = living.getBoundingBox().expand(living.getTargetingMargin()).raycast(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceTo(hit.get());
            if (d >= bestDist) continue;
            HitResult block = mc.world.raycast(new RaycastContext(eye, hit.get(),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (block.getType() != HitResult.Type.MISS) continue;
            bestDist = d;
            best = living;
        }
        return best;
    }

    @Override
    public void onFrame() {
        if (mc.currentScreen != null || mc.player.isUsingItem() || mc.interactionManager == null) return;

        long now = System.currentTimeMillis();
        if (delayMode.is("1.9")) {
            if (mc.player.getAttackCooldownProgress(0.0f) < 1.0f) return;
            if (now - lastAttack < Math.max(60L, (long) delay.d())) return;
        } else {
            if (now - lastAttack < (long) (1000.0 / cps.d())) return;
        }
        if (!hitModeAllows()) return;

        LivingEntity target = findTarget();
        if (target == null) return;

        lastAttack = now;
        if (ThreadLocalRandom.current().nextDouble(100.0) < missChance.d()) {
            // a miss: the arm swings, nothing is hit, and the attack cooldown restarts like in vanilla
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.resetLastAttackedTicks();
            return;
        }
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
