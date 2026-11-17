package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.KeySetting;
import abcxyz.core.ModeSetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.core.Rotations;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

/**
 * Hold the activate key (default H) and it blows up a respawn anchor without hurting you:
 * 0) "Place anchor": when there is no anchor yet it places one from your hotbar, next to the closest player
 *    (Rotate on) or on the block face you are looking at,
 * 1) with Rotate it turns to the anchor under the crosshair, or to the closest one within Range,
 * 2) places a shield block next to the anchor on the side that faces you,
 * 3) charges it with glowstone, 4) explodes it with an item that is not glowstone,
 * 5) "Block after": places a block where the anchor was (Where = Spot) or on top of that spot (Where = Above).
 * It does nothing in the Nether, because anchors don't explode there. Set ActivateKey to None to always be active.
 */
public class SafeAnchor extends Module {
    private final NumSetting range = add(new NumSetting("Range", 4.5, 1, 6, 0.1));
    private final NumSetting delay = add(new NumSetting("Delay", 50, 0, 500, 10));
    private final ModeSetting shield = add(new ModeSetting("Shield", "Obsidian", "Obsidian", "AnyBlock", "Off"));
    private final ModeSetting top = add(new ModeSetting("Top", "Spot", "Spot", "Above"));
    private final BoolSetting placeAfter = add(new BoolSetting("PlaceAfter", true)).with(top);
    private final BoolSetting rotate = add(new BoolSetting("Rotate", true));
    private final NumSetting targetRange = add(new NumSetting("TargetRange", 8, 2, 16, 1));
    private final BoolSetting placeAnchor = add(new BoolSetting("PlaceAnchor", true)).with(targetRange);
    private final KeySetting activateKey = add(new KeySetting("ActivateKey", GLFW.GLFW_KEY_H));
    private final BoolSetting switchBack = add(new BoolSetting("SwitchBack", true));

    private long last = 0L;
    private int previousSlot = -1;
    private int shieldTries = 0;
    private BlockPos lastAnchor = null;

    private int ticks = 0;
    private int rotatedTick = -1;

    private BlockPos topPending = null;
    private long topStart = 0L;

    private BlockPos placedAt = null;
    private long placedTime = 0L;

    public SafeAnchor() {
        super("SafeAnchor", "Hold the key: look at the anchor, shield, charge and explode it safely", Category.Combat);
        activateKey.label = "Activate key";
        placeAfter.label = "Block after";
        top.label = "Where";
        targetRange.label = "Target range";
        placeAnchor.label = "Place anchor";
    }

    @Override
    public String info() {
        return shield.get();
    }

    @Override
    public void onTick() {
        ticks++;
    }

    @Override
    public void onDisable() {
        restoreSlot();
        reset();
    }

    private void reset() {
        shieldTries = 0;
        lastAnchor = null;
        topPending = null;
        placedAt = null;
    }

    private void restoreSlot() {
        if (previousSlot != -1 && switchBack.on() && mc.player != null) {
            mc.player.getInventory().selectedSlot = previousSlot;
        }
        previousSlot = -1;
    }

    private boolean active() {
        int key = activateKey.key();
        return key < 0 || InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
    }

    private int findHotbar(Predicate<ItemStack> filter) {
        for (int i = 0; i < 9; i++) {
            if (filter.test(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    private void select(int slot) {
        if (previousSlot == -1) previousSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;
    }

    private int findBlockSlot(BlockPos ref) {
        return findHotbar(stack -> {
            if (shield.is("AnyBlock")) {
                return stack.getItem() instanceof BlockItem item && !stack.isOf(Items.GLOWSTONE)
                        && !stack.isOf(Items.RESPAWN_ANCHOR) && item.getBlock().getDefaultState().isFullCube(mc.world, ref);
            }
            return stack.isOf(Items.OBSIDIAN);
        });
    }

    private boolean click(BlockHitResult hit) {
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
        return result.isAccepted();
    }

    /** With Rotate on, turns to the point and waits one tick so the server has seen the new rotation. */
    private boolean face(Vec3d point) {
        if (!rotate.on()) return true;
        if (!Rotations.isLookingAt(point, 1.5f)) {
            Rotations.lookAt(point);
            rotatedTick = ticks;
            return false;
        }
        return ticks > rotatedTick;
    }

    private BlockPos findAnchor() {
        if (placedAt != null && mc.world.getBlockState(placedAt).isOf(Blocks.RESPAWN_ANCHOR)) return placedAt;
        if (mc.crosshairTarget instanceof BlockHitResult look && look.getType() == HitResult.Type.BLOCK
                && mc.world.getBlockState(look.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)
                && mc.player.getEyePos().distanceTo(Vec3d.ofCenter(look.getBlockPos())) <= range.d() + 1.0) {
            return look.getBlockPos();
        }
        if (!rotate.on()) return null;
        Vec3d eye = mc.player.getEyePos();
        BlockPos origin = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.d());
        BlockPos best = null;
        double bestDist = range.d() * range.d();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (!mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) continue;
                    double d = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
                    if (d <= bestDist) {
                        bestDist = d;
                        best = pos.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    /** A click on the face of the anchor that points at you. */
    private BlockHitResult hitFor(BlockPos anchor) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d c = Vec3d.ofCenter(anchor);
        Direction side = Direction.getFacing(eye.x - c.x, eye.y - c.y, eye.z - c.z);
        Vec3d pos = c.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        return new BlockHitResult(pos, side, anchor, false);
    }

    /** True when a solid block sits between the explosion (the anchor) and both the eyes and the middle of the player. */
    private boolean isShielded(BlockPos anchor) {
        Vec3d center = Vec3d.ofCenter(anchor);
        Vec3d[] points = {mc.player.getEyePos(), mc.player.getPos().add(0, mc.player.getHeight() / 2.0, 0)};
        for (Vec3d point : points) {
            Vec3d dir = point.subtract(center);
            if (dir.lengthSquared() < 1.0e-4) return false;
            Vec3d start = center.add(dir.normalize().multiply(0.9));
            if (start.squaredDistanceTo(center) >= dir.lengthSquared()) return false;
            HitResult hit = mc.world.raycast(new RaycastContext(start, point,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) return false;
        }
        return true;
    }

    private boolean placeShield(BlockPos anchor) {
        int slot = findBlockSlot(anchor);
        if (slot == -1) return false;

        Vec3d body = mc.player.getPos().add(0, mc.player.getHeight() / 2.0, 0);
        Direction bestSide = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            BlockPos cell = anchor.offset(side);
            if (!mc.world.getBlockState(cell).isReplaceable()) continue;
            if (mc.player.getBoundingBox().intersects(new Box(cell))) continue;
            double d = Vec3d.ofCenter(cell).squaredDistanceTo(body);
            if (d < bestDist) {
                bestDist = d;
                bestSide = side;
            }
        }
        if (bestSide == null) return false;

        select(slot);
        Vec3d hitPos = Vec3d.ofCenter(anchor).add(bestSide.getOffsetX() * 0.5, bestSide.getOffsetY() * 0.5, bestSide.getOffsetZ() * 0.5);
        click(new BlockHitResult(hitPos, bestSide, anchor, false));
        return true;
    }

    private record PlaceSpot(BlockPos place, BlockHitResult hit) {
    }

    private boolean canPlaceAt(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable()
                && mc.world.getOtherEntities(null, new Box(pos), e -> !e.isSpectator()).isEmpty();
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

    /**
     * Where to put the anchor: with Rotate on and a player in TargetRange, next to that player; otherwise on the
     * face of the block you are looking at.
     */
    private PlaceSpot findPlaceSpot() {
        Vec3d eye = mc.player.getEyePos();
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        PlayerEntity target = rotate.on() ? findTarget() : null;
        if (target != null) {
            BlockPos tp = target.getBlockPos();
            PlaceSpot best = null;
            double bestScore = Double.MAX_VALUE;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos place = tp.add(dx, dy, dz);
                        if (!canPlaceAt(place)) continue;
                        Vec3d c = Vec3d.ofCenter(place);
                        double mine = eye.distanceTo(c);
                        if (mine < 1.5 || mine > range.d() + 0.5) continue;
                        for (Direction d : order) {
                            BlockPos support = place.offset(d);
                            if (mc.world.getBlockState(support).isReplaceable()) continue;
                            Direction clicked = d.getOpposite();
                            Vec3d hitPos = Vec3d.ofCenter(support).add(clicked.getOffsetX() * 0.5, clicked.getOffsetY() * 0.5, clicked.getOffsetZ() * 0.5);
                            if (eye.distanceTo(hitPos) > range.d()) continue;
                            double score = c.distanceTo(target.getPos());
                            if (score < bestScore) {
                                bestScore = score;
                                best = new PlaceSpot(place.toImmutable(), new BlockHitResult(hitPos, clicked, support, false));
                            }
                            break;
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        if (mc.crosshairTarget instanceof BlockHitResult h && h.getType() == HitResult.Type.BLOCK) {
            BlockPos place = h.getBlockPos().offset(h.getSide());
            if (canPlaceAt(place)) return new PlaceSpot(place, h);
        }
        return null;
    }

    /** @return true while it is busy (rotating, placing or waiting for the server), false when there is nothing to do. */
    private boolean tryPlaceAnchor(long now) {
        if (placedAt != null) {
            if (now - placedTime < 700) return true; // waiting for the anchor to show up
            placedAt = null;
        }
        int slot = findHotbar(stack -> stack.isOf(Items.RESPAWN_ANCHOR));
        if (slot == -1) {
            last = now;
            mc.player.sendMessage(Text.literal("SafeAnchor: no respawn anchor in hotbar"), true);
            return false;
        }
        PlaceSpot spot = findPlaceSpot();
        if (spot == null) return false;
        if (!face(spot.hit().getPos())) return true;
        select(slot);
        last = now;
        if (click(spot.hit())) {
            placedAt = spot.place();
            placedTime = now;
        }
        return true;
    }

    /** After the explosion: put a block where the anchor was (or on top of that spot). */
    private void handleTop(long now) {
        if (!placeAfter.on() || now - topStart > 2000) {
            finishTop(now);
            return;
        }
        BlockPos spot = topPending;
        if (mc.world.getBlockState(spot).isOf(Blocks.RESPAWN_ANCHOR)) return; // explosion not processed yet
        int slot = findBlockSlot(spot);
        if (slot == -1) {
            mc.player.sendMessage(Text.literal("SafeAnchor: no block in hotbar for the top"), true);
            finishTop(now);
            return;
        }
        BlockPos[] candidates = top.is("Above") ? new BlockPos[]{spot.up(), spot} : new BlockPos[]{spot};
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (BlockPos place : candidates) {
            if (!mc.world.getBlockState(place).isReplaceable()) {
                finishTop(now); // already filled
                return;
            }
            if (mc.player.getBoundingBox().intersects(new Box(place))) continue;
            for (Direction d : order) {
                BlockPos support = place.offset(d);
                if (mc.world.getBlockState(support).isReplaceable()) continue;
                if (!face(Vec3d.ofCenter(spot))) return;
                Direction clicked = d.getOpposite();
                Vec3d hitPos = Vec3d.ofCenter(support).add(clicked.getOffsetX() * 0.5, clicked.getOffsetY() * 0.5, clicked.getOffsetZ() * 0.5);
                select(slot);
                click(new BlockHitResult(hitPos, clicked, support, false));
                finishTop(now);
                return;
            }
        }
        mc.player.sendMessage(Text.literal("SafeAnchor: nothing to place the top block against"), true);
        finishTop(now);
    }

    private void finishTop(long now) {
        topPending = null;
        last = now;
        restoreSlot();
    }

    @Override
    public void onFrame() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (mc.world.getDimension().respawnAnchorWorks()) return;
        long now = System.currentTimeMillis();
        if (now - last < delay.d()) return;

        if (topPending != null) {
            handleTop(now);
            return;
        }

        boolean active = active();
        BlockPos anchor = active ? findAnchor() : null;
        if (anchor == null) {
            if (active && placeAnchor.on() && tryPlaceAnchor(now)) return;
            if (previousSlot != -1) restoreSlot();
            shieldTries = 0;
            lastAnchor = null;
            return;
        }
        if (!anchor.equals(lastAnchor)) {
            lastAnchor = anchor;
            shieldTries = 0;
        }
        if (!face(Vec3d.ofCenter(anchor))) return;

        BlockState state = mc.world.getBlockState(anchor);
        int charges = state.get(RespawnAnchorBlock.CHARGES);

        if (!shield.is("Off") && !isShielded(anchor)) {
            if (shieldTries >= 2) {
                mc.player.sendMessage(Text.literal("SafeAnchor: can't block the explosion, no block placed"), true);
                return;
            }
            last = now;
            if (placeShield(anchor)) {
                shieldTries++;
            } else {
                mc.player.sendMessage(Text.literal("SafeAnchor: no shield block in hotbar"), true);
            }
            return;
        }

        if (charges == 0) {
            int slot = findHotbar(stack -> stack.isOf(Items.GLOWSTONE));
            if (slot == -1) {
                mc.player.sendMessage(Text.literal("SafeAnchor: no glowstone in hotbar"), true);
                return;
            }
            select(slot);
            last = now;
            click(hitFor(anchor));
            return;
        }

        // charged and shielded: explode with something that is not glowstone
        int current = mc.player.getInventory().selectedSlot;
        int slot = !mc.player.getInventory().getStack(current).isOf(Items.GLOWSTONE) ? current
                : findHotbar(stack -> !stack.isOf(Items.GLOWSTONE));
        if (slot == -1) return;
        select(slot);
        last = now;
        if (click(hitFor(anchor))) {
            placedAt = null;
            if (placeAfter.on()) {
                topPending = anchor.toImmutable();
                topStart = now;
            }
        }
    }
}
