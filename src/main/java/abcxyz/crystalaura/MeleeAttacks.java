package abcxyz.crystalaura;

import abcxyz.AbcxyzClient;
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
        return !player.isUsingItem() && !player.isRiding() && !mc.interactionManager.isBreakingBlock();
    }

    public static boolean clickPending(MinecraftClient mc) {
        return false;
    }

    public static boolean clickPending(KeyBinding key) {
        return false;
    }

    public static boolean rightClickComing(MinecraftClient mc) {
        return mc.options.useKey.isPressed();
    }

    public static void claimTick(MinecraftClient mc) {
        if (mc.player != null) {
            noteAction(mc.player);
        }
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
