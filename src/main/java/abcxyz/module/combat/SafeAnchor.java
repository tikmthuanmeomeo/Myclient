package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.ModeSetting;
import abcxyz.core.NumSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
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

import java.util.function.Predicate;

/**
 * Blows up the respawn anchor you are looking at without hurting yourself:
 * 1) places a block next to the anchor on the side that faces you (the shield),
 * 2) charges it with glowstone, 3) explodes it with an item that is not glowstone.
 * It only touches the anchor under your crosshair, and does nothing in the Nether (anchors don't explode there).
 */
public class SafeAnchor extends Module {
    private final NumSetting delay = add(new NumSetting("Delay", 50, 0, 500, 10));
    private final ModeSetting shield = add(new ModeSetting("Shield", "Obsidian", "Obsidian", "AnyBlock", "Off"));
    private final BoolSetting switchBack = add(new BoolSetting("SwitchBack", true));

    private long last = 0L;
    private int previousSlot = -1;
    private int shieldTries = 0;
    private BlockPos lastAnchor = null;

    public SafeAnchor() {
        super("SafeAnchor", "Charges and explodes the anchor you look at behind a shield block", Category.Combat);
    }

    @Override
    public String info() {
        return shield.get();
    }

    @Override
    public void onDisable() {
        restoreSlot();
        shieldTries = 0;
        lastAnchor = null;
    }

    private void restoreSlot() {
        if (previousSlot != -1 && switchBack.on() && mc.player != null) {
            mc.player.getInventory().selectedSlot = previousSlot;
        }
        previousSlot = -1;
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
        int slot = findHotbar(stack -> {
            if (shield.is("Obsidian")) return stack.isOf(Items.OBSIDIAN);
            return stack.getItem() instanceof BlockItem item && !stack.isOf(Items.GLOWSTONE)
                    && !stack.isOf(Items.RESPAWN_ANCHOR) && item.getBlock().getDefaultState().isFullCube(mc.world, anchor);
        });
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
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hitPos, bestSide, anchor, false));
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    @Override
    public void onFrame() {
        if (mc.currentScreen != null || mc.interactionManager == null) return;
        if (mc.world.getDimension().respawnAnchorWorks()) return;

        if (!(mc.crosshairTarget instanceof BlockHitResult look)
                || !mc.world.getBlockState(look.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) {
            if (previousSlot != -1) restoreSlot();
            shieldTries = 0;
            lastAnchor = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - last < delay.d()) return;

        BlockPos anchor = look.getBlockPos();
        if (!anchor.equals(lastAnchor)) {
            lastAnchor = anchor;
            shieldTries = 0;
        }
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
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, look);
            if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }

        // charged and shielded: explode with something that is not glowstone
        int current = mc.player.getInventory().selectedSlot;
        int slot = !mc.player.getInventory().getStack(current).isOf(Items.GLOWSTONE) ? current
                : findHotbar(stack -> !stack.isOf(Items.GLOWSTONE));
        if (slot == -1) return;
        select(slot);
        last = now;
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, look);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
    }
}
