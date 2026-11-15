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

/** Attacks automatically when your crosshair is on a target. */
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
    public void onTick() {
        if (mc.currentScreen != null || mc.player.isUsingItem()) return;
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit)) return;
        Entity target = entityHit.getEntity();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living == mc.player) return;
        boolean isPlayer = target instanceof PlayerEntity;
        if (isPlayer ? !players.on() : !mobs.on()) return;
        if (cooldown.on() && mc.player.getAttackCooldownProgress(0.5f) < 0.95f) return;
        if (onlyCrits.on() && !(mc.player.fallDistance > 0.0f && !mc.player.isOnGround()
                && !mc.player.isClimbing() && !mc.player.isTouchingWater())) return;
        long now = System.currentTimeMillis();
        if (now - lastAttack < delay.d()) return;
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttack = now;
    }
}
