package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Attacks automatically when your crosshair is on a target.
 * It is checked every rendered frame (not only every 50 ms tick), so a target that sweeps through the
 * crosshair is not missed and the hit comes right when the attack cooldown is ready.
 */
public class TriggerBot extends Module {
    private final NumSetting delay = add(new NumSetting("Delay", 0, 0, 500, 10));
    private final BoolSetting cooldown = add(new BoolSetting("Cooldown", true));
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting mobs = add(new BoolSetting("Mobs", false));
    private final BoolSetting onlyCrits = add(new BoolSetting("OnlyCrits", false));

    private long lastAttack = 0L;

    public TriggerBot() {
        super("TriggerBot", "Attacks when your crosshair is on a target", Category.Combat);
    }

    @Override
    public void onFrame() {
        if (mc.currentScreen != null || mc.player.isUsingItem() || mc.interactionManager == null) return;
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit)) return;
        Entity target = entityHit.getEntity();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living == mc.player) return;
        boolean isPlayer = target instanceof PlayerEntity;
        if (isPlayer ? !players.on() : !mobs.on()) return;
        if (cooldown.on() && mc.player.getAttackCooldownProgress(0.0f) < 1.0f) return;
        if (onlyCrits.on() && !(mc.player.fallDistance > 0.0f && !mc.player.isOnGround()
                && !mc.player.isClimbing() && !mc.player.isTouchingWater())) return;
        long now = System.currentTimeMillis();
        long minGap = Math.max(60L, (long) delay.d());
        if (now - lastAttack < minGap) return;
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttack = now;
    }
}
