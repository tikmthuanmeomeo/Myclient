package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Explosions;
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
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Predicate;

/**
 * Hold the activate key (default H) and it blows up a respawn anchor without hurting you:
 * 0) "Place anchor": when there is no anchor yet it places one from your hotbar. Mode Target = next to the closest
 *    player within Target range (on the block face you look at when nobody is around), Mode Crosshair = always on
 *    the block face you are looking at,
 * 1) with Rotate it turns to the anchor under the crosshair, or to the closest one within Range,
 * 2) places shield blocks around the anchor until the damage the explosion would do to you (estimated like vanilla,
 *    with exposure rays, armor and resistance) is at most "Max self damage",
 * 3) charges it with glowstone, 4) explodes it with an item that is not glowstone, only while the estimated damage
 *    to you is at most "Max self damage" (and never when it would kill you),
 * 5) "Block after": places a block where the anchor was (Where = Spot) or on top of that spot (Where = Above).
 * It does nothing in the Nether, because anchors don't explode there. Set ActivateKey to None to always be active.
 */
public class SafeAnchor extends Module {
    private final NumSetting range = add(new NumSetting("Range", 4.5, 1, 6, 0.1));
    private final NumSetting delay = add(new NumSetting("Delay", 50, 0, 500, 10));
    private final ModeSetting shield = add(new ModeSetting("Shield", "Obsidian", "Obsidian", "AnyBlock", "Off"));
    private final NumSetting maxSelf = add(new NumSetting("MaxSelfDamage", 2.0, 0, 36, 0.5));
    private final ModeSetting top = add(new ModeSetting("Top", "Spot", "Spot", "Above"));
    private final BoolSetting placeAfter = add(new BoolSetting("PlaceAfter", true)).with(top);
    private final BoolSetting rotate = add(new BoolSetting("Rotate", true));
    private final NumSetting targetRange = add(new NumSetting("TargetRange", 8, 2, 16, 1));
    private final ModeSetting placeMode = add(new ModeSetting("PlaceMode", "Target", "Target", "Crosshair"));
    private final BoolSetting placeAnchor = add(new BoolSetting("PlaceAnchor", true)).with(targetRange, placeMode);
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
    private long lastSpotMessage = 0L;

    public SafeAnchor() {
        super("SafeAnchor", "Hold the key: look at the anchor, shield, charge and explode it safely", Category.Combat);
        activateKey.label = "Activate key";
        maxSelf.label = "Max self damage";
        placeAfter.label = "Block after";
        top.label = "Where";
        targetRange.label = "Target range";
        placeMode.label = "Mode";
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

    /** Damage the explosion of this anchor would deal to you, with the given extra blocks planned around it. */
    private float selfDamage(BlockPos anchor, List<Box> extra) {
        return Explosions.damage(Vec3d.ofCenter(anchor), Explosions.ANCHOR_POWER, mc.player, anchor, extra);
    }

    /** A click that places a block in the cell against a solid neighbour (not against a charged anchor: that would blow it up). */
    private BlockHitResult supportClick(BlockPos cell, BlockPos anchor, boolean charged) {
        Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (Direction d : order) {
            BlockPos support = cell.offset(d);
            if (mc.world.getBlockState(support).isReplaceable()) continue;
            if (charged && support.equals(anchor)) continue;
            Direction clicked = d.getOpposite();
            Vec3d hitPos = Vec3d.ofCenter(support).add(clicked.getOffsetX() * 0.5, clicked.getOffsetY() * 0.5, clicked.getOffsetZ() * 0.5);
            return new BlockHitResult(hitPos, clicked, support, false);
        }
        return null;
    }

    /**
     * Places the one block around the anchor (all 26 cells, diagonals too) that lowers the damage to you the most.
     *
     * @return 0 = placed, 1 = no shield block in the hotbar, 2 = no spot would lower the damage
     */
    private int placeShield(BlockPos anchor, boolean charged, float current) {
        int slot = findBlockSlot(anchor);
        if (slot == -1) return 1;

        Vec3d eye = mc.player.getEyePos();
        Vec3d body = mc.player.getPos().add(0, mc.player.getHeight() / 2.0, 0);
        BlockPos bestCell = null;
        BlockHitResult bestHit = null;
        float bestDamage = current - 0.05f;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos cell = anchor.add(dx, dy, dz);
                    if (!canPlaceAt(cell)) continue;
                    BlockHitResult hit = supportClick(cell, anchor, charged);
                    if (hit == null || eye.distanceTo(hit.getPos()) > range.d()) continue;
                    float dmg = selfDamage(anchor, List.of(new Box(cell)));
                    double dist = Vec3d.ofCenter(cell).squaredDistanceTo(body);
                    if (dmg < bestDamage - 0.01f || (dmg <= bestDamage + 0.01f && bestCell != null && dist < bestDist)) {
                        bestDamage = Math.min(dmg, bestDamage);
                        bestDist = dist;
                        bestCell = cell.toImmutable();
                        bestHit = hit;
                    }
                }
            }
        }
        if (bestCell == null) return 2;

        select(slot);
        click(bestHit);
        return 0;
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
        PlayerEntity target = placeMode.is("Target") ? findTarget() : null;
        if (target != null) {
            BlockPos tp = target.getBlockPos();
            PlaceSpot best = null;
            double bestScore = Double.MAX_VALUE;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos place = tp.add(dx, dy, dz);
                        if (!canPlaceAt(place)) continue;
                        Vec3d c = Vec3d.ofCenter(place);
                        double mine = eye.distanceTo(c);
                        if (mine < 2.0 || mine > range.d() + 0.5) continue;
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
            if (System.currentTimeMillis() - lastSpotMessage > 1000) {
                lastSpotMessage = System.currentTimeMillis();
                mc.player.sendMessage(Text.literal("SafeAnchor: no spot to place an anchor next to " + target.getName().getString()
                        + " (get closer, Range " + range.d() + ")"), true);
            }
            return null;
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

        float self = selfDamage(anchor, List.of());
        boolean deadly = self >= mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (!shield.is("Off") && (self > maxSelf.d() || deadly)) {
            if (shieldTries >= 4) {
                mc.player.sendMessage(Text.literal("SafeAnchor: can't block the explosion enough (~" + Math.round(self * 10) / 10.0
                        + " damage, max " + maxSelf.d() + "), no block placed"), true);
                return;
            }
            last = now;
            int result = placeShield(anchor, charges > 0, self);
            if (result == 0) {
                shieldTries++;
            } else if (result == 1) {
                mc.player.sendMessage(Text.literal("SafeAnchor: no shield block in hotbar"), true);
            } else {
                mc.player.sendMessage(Text.literal("SafeAnchor: no block spot lowers the damage (~" + Math.round(self * 10) / 10.0
                        + ", max " + maxSelf.d() + ")"), true);
            }
            return;
        }
        if (self > maxSelf.d() || deadly) {
            mc.player.sendMessage(Text.literal("SafeAnchor: explosion would deal ~" + Math.round(self * 10) / 10.0
                    + " damage to you (max " + maxSelf.d() + "), not exploding"), true);
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
