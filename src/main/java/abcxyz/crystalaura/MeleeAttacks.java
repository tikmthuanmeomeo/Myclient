package abcxyz.crystalaura;

import abcxyz.AbcxyzClient;
import abcxyz.mixin.KeyBindingAccessor;
import abcxyz.mixin.MinecraftClientAccessor;
import abcxyz.module.render.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** The few helpers of the crystal aura that need to know about the player's hands, targets and line of sight. */
public final class MeleeAttacks {
    private static int actorId = -1;
    private static int actionTick = -1;

    private MeleeAttacks() {
    }

    public static void noteAction(PlayerEntity player) {
        actorId = player.getId();
        actionTick = player.age;
    }

    public static boolean actedThisTick(PlayerEntity player) {
        return actionTick == player.age && actorId == player.getId();
    }

    public static boolean handsFree(MinecraftClient mc, ClientPlayerEntity player) {
        return !player.isUsingItem() && !player.isRiding() && !mc.interactionManager.isBreakingBlock()
                && !clickPending(mc.options.swapHandsKey) && !clickPending(mc.options.dropKey);
    }

    public static boolean clickPending(MinecraftClient mc) {
        return clickPending(mc.options.attackKey);
    }

    /** The player has clicked this key and the game has not acted on it yet. */
    public static boolean clickPending(KeyBinding key) {
        return ((KeyBindingAccessor) key).abcxyz$timesPressed() > 0;
    }

    public static boolean rightClickComing(MinecraftClient mc) {
        return mc.options.useKey.isPressed() || clickPending(mc.options.useKey);
    }

    public static void takeAttackClicks(MinecraftClient mc) {
        KeyBinding attack = mc.options.attackKey;
        while (attack.wasPressed()) {
        }
    }

    /**
     * The module acted this tick: throw away the player's own pending clicks and keep the game from using the item in
     * hand again for a moment, otherwise the game places or uses it a second time (ghost blocks, rubber banding).
     */
    public static void claimTick(MinecraftClient mc) {
        takeAttackClicks(mc);
        KeyBinding use = mc.options.useKey;
        while (use.wasPressed()) {
        }
        MinecraftClientAccessor access = (MinecraftClientAccessor) mc;
        access.abcxyz$setAttackCooldown(Math.max(access.abcxyz$attackCooldown(), 1));
        access.abcxyz$setItemUseCooldown(Math.max(access.abcxyz$itemUseCooldown(), 2));
    }

    public static boolean canAct(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            return false;
        }
        Freecam freecam = AbcxyzClient.modules != null ? AbcxyzClient.modules.get(Freecam.class) : null;
        return freecam == null || !freecam.isActive();
    }

    public static boolean lineIsClear(ClientPlayerEntity player, Vec3d from, Vec3d to) {
        BlockHitResult block = player.getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, (Entity)player));
        return block.getType() == HitResult.Type.MISS || block.getPos().squaredDistanceTo(from) >= from.squaredDistanceTo(to) - 0.001;
    }

    private static boolean isHittable(ClientPlayerEntity self, Entity entity) {
        if (entity == self || !(entity instanceof LivingEntity living)) {
            return false;
        }
        if (!living.isAlive() || !entity.isAttackable() || self.getVehicle() == entity) {
            return false;
        }
        return !(entity instanceof PlayerEntity player) || !player.isSpectator() && !player.isCreative();
    }

    public static boolean isEnemy(ClientPlayerEntity self, Entity entity) {
        if (!isHittable(self, entity) || isFriend(entity)) {
            return false;
        }
        return !(entity instanceof PlayerEntity player) || self.shouldDamagePlayer(player) && self.networkHandler.getPlayerListEntry(player.getUuid()) != null;
    }

    /** There is no friend list in abcxyz. */
    public static boolean isFriend(Entity entity) {
        return false;
    }
}
