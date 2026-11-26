package abcxyz.module.combat;

import abcxyz.AbcxyzClient;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.KeySetting;
import abcxyz.core.ModeSetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.core.Rotations;
import abcxyz.crystalaura.MeleeAttacks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.InputUtil;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.function.Predicate;

/**
 * TNT minecart setups. Bow: after you shoot an arrow it puts a rail and a TNT minecart where the arrow will land.
 * CrossBow: press the key and it puts a rail and a TNT minecart on the block you look at, lights it (fire from flint and
 * steel, or a Flame crossbow) and shoots the minecart. Cart Aura: does the same on its own at a player whose totem just
 * popped. The module turns only the server's view of you (silent rotation) unless Change look is on.
 * <p>
 * Based on the AutoCart of V-Core (MIT License, (c) 2026 ngminhthuan), see THIRD_PARTY_NOTICES/VCore-LICENSE.txt.
 */
public class AutoCart extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Bow", "Bow", "CrossBow"));
    private final NumSetting maxDistance = add(new NumSetting("MaxDistance", 4.5, 2.0, 6.0, 0.1));
    private final NumSetting startDelay = add(new NumSetting("StartDelay", 25, 0, 60, 1));
    private final KeySetting activeKey = add(new KeySetting("ActiveKey", -1));
    private final NumSetting delay = add(new NumSetting("Delay", 25, 0, 100, 5));
    private final NumSetting cartAuraDelay = add(new NumSetting("CartAuraDelay", 0, 0, 20, 1));
    private final BoolSetting swapBack = add(new BoolSetting("SwapBack", true));
    private final BoolSetting changeLook = add(new BoolSetting("ChangeLook", false));
    private final BoolSetting auraTarget = add(new BoolSetting("AuraTarget", true));
    private final BoolSetting otherPlayer = add(new BoolSetting("OtherPlayer", false));
    private final BoolSetting cartAura = add(new BoolSetting("CartAura", false)).with(cartAuraDelay, auraTarget, otherPlayer);
    private final NumSetting refillSlot = add(new NumSetting("RefillSlot", 9, 1, 9, 1));
    private final ModeSetting refill = add(new ModeSetting("ReFill", "None", "None", "Instant"));

    private final ArrayDeque<Runnable> steps = new ArrayDeque<>();
    private final ArrayDeque<Long> waits = new ArrayDeque<>();
    private long nextAt = 0L;
    private boolean running = false;

    private boolean wasUsingBow = false;
    private int lastUseTime = 0;
    private float lastBowYaw;
    private float lastBowPitch;
    private boolean keyWasDown = false;

    private float lookYaw;
    private float lookPitch;
    private boolean looking = false;

    public AutoCart() {
        super("AutoCart", "TNT minecart setups with a bow or a crossbow", Category.Combat);
        maxDistance.label = "Max distance";
        startDelay.label = "Start delay (ms)";
        activeKey.label = "Active key";
        delay.label = "Delay (ms)";
        cartAuraDelay.label = "Cart aura delay (ticks)";
        swapBack.label = "Swap back";
        changeLook.label = "Change look";
        auraTarget.label = "Aura target";
        otherPlayer.label = "Other player";
        cartAura.label = "Cart aura";
        refillSlot.label = "Refill slot";
        refill.label = "Refill";
    }

    @Override
    public String info() {
        return mode.get();
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        steps.clear();
        waits.clear();
        running = false;
        looking = false;
        wasUsingBow = false;
        keyWasDown = false;
    }

    // ------------------------------------------------------------------ the step runner

    /** Runs the action after the given wait (ms), after everything that was queued before. */
    private void queue(long waitMs, Runnable action) {
        steps.add(action);
        waits.add(waitMs);
    }

    private void start() {
        running = true;
        nextAt = System.currentTimeMillis();
    }

    @Override
    public void onFrame() {
        if (mc.player == null || mc.world == null || !running) return;
        long now = System.currentTimeMillis();
        if (now < nextAt) return;
        Runnable action = steps.poll();
        Long wait = waits.poll();
        if (action == null) {
            running = false;
            return;
        }
        try {
            action.run();
        } catch (Throwable t) {
            steps.clear();
            waits.clear();
            running = false;
            looking = false;
            return;
        }
        nextAt = System.currentTimeMillis() + (wait == null ? 0L : wait);
        if (steps.isEmpty()) running = false;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        handleRefill();
        if (mode.is("Bow")) {
            ItemStack main = mc.player.getMainHandStack();
            boolean using = mc.player.isUsingItem() && main.isOf(Items.BOW);
            if (using) {
                lastUseTime = mc.player.getItemUseTime();
                lastBowYaw = mc.player.getYaw();
                lastBowPitch = mc.player.getPitch();
            } else if (wasUsingBow && lastUseTime >= 3) {
                onBowShot();
            }
            wasUsingBow = using;
            return;
        }
        if (mc.currentScreen != null) return;
        boolean down = activeKey.key() >= 0 && InputUtil.isKeyPressed(mc.getWindow().getHandle(), activeKey.key());
        if (down && !keyWasDown && !running) executeCrossbow();
        keyWasDown = down;
    }

    // ------------------------------------------------------------------ bow mode

    private void onBowShot() {
        if (running) return;
        int bow = hotbar(stack -> stack.isOf(Items.BOW));
        int cart = hotbar(stack -> stack.isOf(Items.TNT_MINECART));
        if (bow == -1 || cart == -1) return;
        BlockPos hit = bowTrajectory();
        if (hit == null) return;
        BlockPos base = cartBase(hit);
        boolean rail = isRail(mc.world.getBlockState(base.up()).getBlock());
        if (!rail && findRail() == -1) return;
        double distSq = mc.player.getEyePos().squaredDistanceTo(base.up().toCenterPos());
        double maxSq = maxDistance.d() * maxDistance.d();
        if (distSq > maxSq || distSq < 4.0) return;
        placeSequence(base, rail, startDelay.i(), false, -1, -1, false, -1);
    }

    /** Where the arrow of the shot that was just released will land (null: it hits an entity or nothing). */
    private BlockPos bowTrajectory() {
        Vec3d pos = mc.player.getEyePos().add(0.0, -0.1, 0.0);
        double yawRad = lastBowYaw / 180.0f * Math.PI;
        double pitchRad = lastBowPitch / 180.0f * Math.PI;
        double mx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double my = -Math.sin(pitchRad);
        double mz = Math.cos(yawRad) * Math.cos(pitchRad);
        float power = lastUseTime / 20.0f;
        power = (power * power + power * 2.0f) / 3.0f;
        if (power > 1.0f) power = 1.0f;
        if (power < 0.1f) return null;
        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        double speed = power * 3.0f / len;
        mx *= speed;
        my *= speed;
        mz *= speed;
        if (!mc.player.isOnGround()) my += mc.player.getVelocity().y;
        double x = pos.x, y = pos.y, z = pos.z;
        for (int i = 0; i < 300; i++) {
            Vec3d last = new Vec3d(x, y, z);
            x += mx;
            y += my;
            z += mz;
            mx *= 0.99;
            my *= 0.99;
            mz *= 0.99;
            my -= 0.05;
            Box probe = new Box(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3);
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof ArrowEntity || entity == mc.player || !entity.getBoundingBox().intersects(probe)) continue;
                return null;
            }
            BlockHitResult hit = mc.world.raycast(new RaycastContext(last, new Vec3d(x, y, z), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, (Entity) mc.player));
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) return hit.getBlockPos();
            if (y <= -65.0) break;
        }
        return null;
    }

    private BlockPos cartBase(BlockPos target) {
        BlockState state = mc.world.getBlockState(target);
        return isRail(state.getBlock()) || state.isReplaceable() ? target.down() : target;
    }

    // ------------------------------------------------------------------ crossbow mode and cart aura

    private void executeCrossbow() {
        int crossbow = hotbar(this::isCharged);
        int rail = findRail();
        int cart = hotbar(stack -> stack.isOf(Items.TNT_MINECART));
        if (crossbow == -1 || rail == -1 || cart == -1) return;
        boolean flame = hasFlame(mc.player.getInventory().getStack(crossbow));
        int flint = hotbar(stack -> stack.isOf(Items.FLINT_AND_STEEL));
        if (!flame && flint == -1) return;
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult ray = mc.world.raycast(new RaycastContext(eye, eye.add(mc.player.getRotationVec(1.0f).multiply(4.5)),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, (Entity) mc.player));
        if (ray == null || ray.getType() != HitResult.Type.BLOCK) return;
        BlockPos hit = ray.getBlockPos();
        BlockPos base = mc.world.getBlockState(hit).isReplaceable() ? hit.down() : hit;
        if (!flame && findFire(base) == null) return;
        placeSequence(base, isRail(mc.world.getBlockState(base.up()).getBlock()), 0, true, crossbow, flint, flame, cart);
    }

    /** Called when a player's totem popped (EntityStatus 35, from ClientPlayNetworkHandlerMixin). */
    public void onTotemPop(PlayerEntity popped) {
        if (!mode.is("CrossBow") || !cartAura.on() || running || popped == mc.player || mc.player == null) return;
        Aura aura = AbcxyzClient.modules == null ? null : AbcxyzClient.modules.get(Aura.class);
        boolean auraHit = auraTarget.on() && aura != null && aura.isEnabled() && aura.target == popped;
        boolean other = otherPlayer.on() && mc.player.getPos().distanceTo(popped.getPos()) <= 6.0;
        if (!auraHit && !other) return;
        BlockPos base = cartAuraPosition(popped);
        if (base == null) return;
        int crossbow = hotbar(this::isCharged);
        int cart = hotbar(stack -> stack.isOf(Items.TNT_MINECART));
        if (crossbow == -1 || cart == -1) return;
        boolean flame = hasFlame(mc.player.getInventory().getStack(crossbow));
        int flint = hotbar(stack -> stack.isOf(Items.FLINT_AND_STEEL));
        if (!flame && flint == -1) return;
        boolean rail = isRail(mc.world.getBlockState(base.up()).getBlock());
        if (!rail && findRail() == -1) return;
        if (!flame && findFire(base) == null) return;
        placeSequence(base, rail, cartAuraDelay.i() * 50, true, crossbow, flint, flame, cart);
    }

    private BlockPos cartAuraPosition(Entity target) {
        Box box = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        BlockPos at = target.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = at.getX() - 4; x <= at.getX() + 4; x++) {
            for (int z = at.getZ() - 4; z <= at.getZ() + 4; z++) {
                for (int y = at.getY() - 4; y <= at.getY(); y++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (!mc.world.getBlockState(bp).isSolid() || bp.getY() + 1 > box.minY) continue;
                    BlockState above = mc.world.getBlockState(bp.up());
                    if (!above.isReplaceable() && !isRail(above.getBlock())) continue;
                    Vec3d top = new Vec3d(x + 0.5, y + 1.0, z + 0.5);
                    if (eye.squaredDistanceTo(top) > 4.5 * 4.5) continue;
                    BlockHitResult sight = mc.world.raycast(new RaycastContext(eye, top, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, (Entity) mc.player));
                    if (sight.getType() != HitResult.Type.MISS && !sight.getBlockPos().equals(bp)) continue;
                    double d = top.distanceTo(new Vec3d(target.getX(), box.minY, target.getZ()));
                    if (d < bestDist) {
                        bestDist = d;
                        best = bp;
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the placing sequence

    /**
     * Rail (if missing), TNT minecart, then (CrossBow) fire from flint and steel and the shot at the minecart.
     * crossbow, flint and cart are hotbar slots (-1: not used).
     */
    private void placeSequence(BlockPos base, boolean railExists, long firstWait, boolean shoot, int crossbow, int flint, boolean flame, int cart) {
        int prevSlot = mc.player.getInventory().selectedSlot;
        long step = delay.i();
        int railSlot = findRail();
        int cartSlot = cart != -1 ? cart : hotbar(stack -> stack.isOf(Items.TNT_MINECART));
        BlockPos fire = shoot && !flame ? findFire(base) : null;
        Vec3d placeVec = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
        steps.clear();
        waits.clear();
        queue(firstWait, () -> look(placeVec));
        if (!railExists) {
            queue(step, () -> {
                if (!isRail(mc.world.getBlockState(base.up()).getBlock())) {
                    select(railSlot);
                    interactBlock(new BlockHitResult(placeVec, Direction.UP, base, false));
                }
            });
        }
        queue(step, () -> {
            select(cartSlot);
            interactBlock(new BlockHitResult(new Vec3d(base.getX() + 0.5, base.getY() + 1.125, base.getZ() + 0.5), Direction.UP, base.up(), false));
        });
        if (shoot) {
            if (fire != null) {
                Vec3d fireVec = new Vec3d(fire.getX() + 0.5, fire.getY() + 1.0, fire.getZ() + 0.5);
                queue(step, () -> {
                    look(fireVec);
                    select(flint);
                    interactBlock(new BlockHitResult(fireVec, Direction.UP, fire, false));
                });
            }
            queue(step, () -> {
                look(new Vec3d(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5));
                select(crossbow);
                interactItem();
            });
        }
        queue(0L, () -> {
            if (swapBack.on()) select(prevSlot);
            looking = false;
        });
        start();
    }

    private void look(Vec3d point) {
        Vec3d eye = mc.player.getEyePos();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        lookYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        lookPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, dist)), -90.0f, 90.0f);
        looking = true;
        if (changeLook.on()) {
            mc.player.setYaw(lookYaw);
            mc.player.setPitch(lookPitch);
        }
    }

    private void select(int slot) {
        if (slot >= 0 && slot < 9) mc.player.getInventory().selectedSlot = slot;
    }

    private void interactBlock(BlockHitResult hit) {
        MeleeAttacks.claimTick(mc);
        boolean silent = looking && !changeLook.on();
        if (silent) Rotations.silentLookAngles(lookYaw, lookPitch);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (silent) Rotations.restore();
    }

    private void interactItem() {
        MeleeAttacks.claimTick(mc);
        boolean silent = looking && !changeLook.on();
        if (silent) Rotations.silentLookAngles(lookYaw, lookPitch);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (silent) Rotations.restore();
    }

    // ------------------------------------------------------------------ refill

    private void handleRefill() {
        if (refill.is("None") || running || mc.currentScreen != null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        int hotbarSlot = refillSlot.i() - 1;
        if (mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TNT_MINECART)) return;
        for (int slot = 9; slot < 36; slot++) {
            if (mc.player.getInventory().getStack(slot).isOf(Items.TNT_MINECART)) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, hotbarSlot, SlotActionType.SWAP, mc.player);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ small helpers

    private int hotbar(Predicate<ItemStack> filter) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && filter.test(stack)) return slot;
        }
        return -1;
    }

    private int findRail() {
        return hotbar(stack -> {
            Item item = stack.getItem();
            return item == Items.RAIL || item == Items.POWERED_RAIL || item == Items.DETECTOR_RAIL || item == Items.ACTIVATOR_RAIL;
        });
    }

    private boolean isRail(Block block) {
        return block == Blocks.RAIL || block == Blocks.POWERED_RAIL || block == Blocks.DETECTOR_RAIL || block == Blocks.ACTIVATOR_RAIL;
    }

    private boolean isCharged(ItemStack stack) {
        return stack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(stack);
    }

    private boolean hasFlame(ItemStack stack) {
        try {
            var entry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.FLAME);
            return EnchantmentHelper.getLevel(entry, stack) > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** A block next to the minecart's base where fire can be placed, preferring the one nearest to the line to the cart. */
    private BlockPos findFire(BlockPos base) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d cart = new Vec3d(base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5);
        Vec3d dir = cart.subtract(eye).normalize();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos candidate = base.offset(d);
            if (!mc.world.getBlockState(candidate).isSolid() || !mc.world.isAir(candidate.up())) continue;
            Vec3d fire = new Vec3d(candidate.getX() + 0.5, candidate.getY() + 1.0, candidate.getZ() + 0.5);
            Vec3d toFire = fire.subtract(eye);
            double t = toFire.dotProduct(dir);
            if (t < 0.0 || toFire.length() > 4.5) continue;
            double score = toFire.subtract(dir.multiply(t)).length();
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                BlockPos adj = base.offset(d);
                if (mc.world.getBlockState(adj).isSolid() && mc.world.isAir(adj.up())) return adj;
            }
        }
        return best;
    }
}
