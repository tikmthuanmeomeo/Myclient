/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.crystalaura;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.BlockView;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

public final class AnchorGuard {
    public static final float BLAST_POWER = 5.0f;
    private static final double MIN_SHADE = 0.15;
    private static final double FACE_INSET = 0.3;
    private static final double AIM_INSET = 0.1;

    private AnchorGuard() {
    }

    public static Spot find(ClientWorld level, ClientPlayerEntity player, BlockPos anchor, Vec3d sightline) {
        Vec3d blast = Vec3d.ofCenter((Vec3i)anchor);
        Box body = player.getBoundingBox();
        List<Vec3d> points = AnchorGuard.bodyPoints(body);
        ArrayList<Vec3d> open = new ArrayList<Vec3d>(points.size());
        for (Vec3d point : points) {
            BlockHitResult hit = level.raycast(new RaycastContext(point, blast, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, (Entity)player));
            if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals((Object)anchor)) continue;
            open.add(point);
        }
        BlockPos feet = player.getBlockPos();
        if ((double)open.size() < Math.ceil((double)points.size() * 0.15)) {
            return new Spot(feet, null, null, null);
        }
        int needed = (int)Math.ceil((double)open.size() * 0.15);
        Vec3d eye = player.getEyePos();
        LinkedHashSet<BlockPos> around = new LinkedHashSet<BlockPos>();
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                if (dx == 0 && dz == 0) continue;
                around.add(feet.add(dx, 0, dz));
            }
        }
        for (Direction side : Direction.Type.HORIZONTAL) {
            around.add(anchor.offset(side));
        }
        ArrayList<Cell> cells = new ArrayList<Cell>(around.size());
        for (BlockPos pos : around) {
            int shade;
            Box box = new Box(pos);
            if (pos.equals((Object)anchor) || box.intersects(body) || box.raycast(eye, sightline).isPresent() || (shade = AnchorGuard.shade(box, blast, open)) < needed) continue;
            cells.add(new Cell(pos, shade, box.getCenter().squaredDistanceTo(blast)));
        }
        cells.sort(Comparator.comparingInt(Cell::shade).reversed().thenComparingDouble(Cell::distance));
        for (Cell cell : cells) {
            Spot spot;
            BlockState state = level.getBlockState(cell.pos());
            if (!state.isReplaceable() || (spot = state.getOutlineShape((BlockView)level, cell.pos()).isEmpty() ? AnchorGuard.supportFor(level, player, eye, cell.pos()) : AnchorGuard.direct(level, player, eye, cell.pos(), state)) == null) continue;
            return spot;
        }
        return null;
    }

    public static BlockHitResult hitAlong(ClientWorld level, ClientPlayerEntity player, Rotation look, Spot spot) {
        BlockHitResult hit = AnchorGuard.trace(level, player, look);
        if (hit == null || !hit.getBlockPos().equals((Object)spot.support())) {
            return null;
        }
        if (spot.face() != null && hit.getSide() != spot.face()) {
            return null;
        }
        BlockPos placed = Crystals.placedObsidian(level, player, hit, level.getBlockState(spot.support()));
        return spot.pos().equals((Object)placed) ? hit : null;
    }

    public static BlockHitResult anchorHit(ClientWorld level, ClientPlayerEntity player, BlockPos anchor) {
        Rotation sent = ServerRotation.sent();
        BlockHitResult hit = sent == null ? null : AnchorGuard.trace(level, player, sent);
        return hit != null && hit.getBlockPos().equals((Object)anchor) ? hit : null;
    }

    public static boolean isGuardBlock(ItemStack stack) {
        Item class_17922 = stack.getItem();
        if (!(class_17922 instanceof BlockItem)) {
            return false;
        }
        BlockItem item = (BlockItem)class_17922;
        Block block = item.getBlock();
        if (block instanceof BlockEntityProvider || block instanceof FallingBlock || block == Blocks.RESPAWN_ANCHOR || block == Blocks.TNT) {
            return false;
        }
        return block.getDefaultState().isFullCube((BlockView)EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    public static int detonatorSlot(PlayerInventory inventory) {
        int totem = Crystals.hotbarSlotOf(inventory, Items.TOTEM_OF_UNDYING);
        if (totem != -1) {
            return totem;
        }
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); ++slot) {
            if (!Crystals.isCombatItem(inventory.getStack(slot))) continue;
            return slot;
        }
        return -1;
    }

    public static int detonatorRoute(PlayerInventory inventory, ItemStack main) {
        if (Crystals.isCombatItem(main)) {
            return -2;
        }
        int slot = AnchorGuard.detonatorSlot(inventory);
        return slot == -1 ? -1 : slot;
    }

    public static Vec3d anchorAim(ClientWorld level, ClientPlayerEntity player, BlockPos anchor) {
        Vec3d point;
        BlockHitResult hit;
        Vec3d eye = player.getEyePos();
        Box box = new Box(anchor).contract(0.1);
        Rotation sent = ServerRotation.sent();
        if (sent != null) {
            Vec3d direction = Vec3d.fromPolar((float)sent.pitch(), (float)sent.yaw());
            double along = box.getCenter().subtract(eye).dotProduct(direction);
            if (along > 0.0) {
                Vec3d on = eye.add(direction.multiply(along));
                Vec3d toward = new Vec3d(MathHelper.clamp((double)on.x, (double)box.minX, (double)box.maxX), MathHelper.clamp((double)on.y, (double)box.minY, (double)box.maxY), MathHelper.clamp((double)on.z, (double)box.minZ, (double)box.maxZ));
                BlockHitResult hit2 = AnchorGuard.trace(level, player, ServerRotation.next(player, RotationUtils.fromTo(eye, toward)));
                if (hit2 != null && hit2.getBlockPos().equals((Object)anchor)) {
                    return toward;
                }
            }
        }
        return (hit = AnchorGuard.trace(level, player, ServerRotation.next(player, RotationUtils.fromTo(eye, point = new Vec3d(MathHelper.clamp((double)eye.x, (double)box.minX, (double)box.maxX), MathHelper.clamp((double)eye.y, (double)box.minY, (double)box.maxY), MathHelper.clamp((double)eye.z, (double)box.minZ, (double)box.maxZ)))))) != null && hit.getBlockPos().equals((Object)anchor) ? point : null;
    }

    public static void click(MinecraftClient mc, ClientPlayerEntity player, BlockHitResult hit) {
        ActionResult.Success success;
        ItemStack held = player.getMainHandStack();
        int countBefore = held.getCount();
        ActionResult result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        if (result instanceof ActionResult.Success && (success = (ActionResult.Success)result).swingSource() == ActionResult.SwingSource.CLIENT) {
            player.swingHand(Hand.MAIN_HAND);
            if (!held.isEmpty() && (held.getCount() != countBefore || player.isInCreativeMode())) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND);
            }
        }
        MeleeAttacks.claimTick(mc);
    }

    private static Spot direct(ClientWorld level, ClientPlayerEntity player, Vec3d eye, BlockPos pos, BlockState state) {
        Spot spot = new Spot(pos, pos, null, state.getOutlineShape((BlockView)level, pos).getBoundingBox().offset(pos).getCenter());
        Rotation look = ServerRotation.next(player, RotationUtils.fromTo(eye, spot.aim()));
        return AnchorGuard.hitAlong(level, player, look, spot) != null ? spot : null;
    }

    public static Spot supportFor(ClientWorld level, ClientPlayerEntity player, Vec3d eye, BlockPos cell) {
        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            double distance;
            VoxelShape shape;
            BlockPos support = cell.offset(side);
            BlockState state = level.getBlockState(support);
            if (state.isReplaceable() || (shape = state.getOutlineShape((BlockView)level, support)).isEmpty()) continue;
            Direction face = side.getOpposite();
            Spot spot = null;
            for (Vec3d aim : AnchorGuard.faceAims(shape.getBoundingBox().offset(support), face)) {
                Spot candidate = new Spot(cell, support, face, aim);
                Rotation look = ServerRotation.next(player, RotationUtils.fromTo(eye, aim));
                if (AnchorGuard.hitAlong(level, player, look, candidate) == null) continue;
                spot = candidate;
                break;
            }
            if (spot == null) continue;
            double d = distance = side == Direction.DOWN ? -1.0 : eye.squaredDistanceTo(spot.aim());
            if (!(distance < bestDistance)) continue;
            best = spot;
            bestDistance = distance;
        }
        return best;
    }

    private static List<Vec3d> faceAims(Box box, Direction face) {
        Direction.Axis axis = face.getAxis();
        double plane = face.getDirection() == Direction.AxisDirection.POSITIVE ? box.getMax(axis) : box.getMin(axis);
        Vec3d center = box.getCenter().withAxis(axis, plane);
        ArrayList<Vec3d> aims = new ArrayList<Vec3d>(5);
        aims.add(center);
        for (Direction toward : Direction.values()) {
            Direction.Axis along = toward.getAxis();
            if (along == axis) continue;
            aims.add(center.offset(toward, (box.getMax(along) - box.getMin(along)) * 0.3));
        }
        return aims;
    }

    public static BlockHitResult trace(ClientWorld level, ClientPlayerEntity player, Rotation look) {
        Vec3d reach;
        Vec3d eye = player.getEyePos();
        BlockHitResult hit = level.raycast(new RaycastContext(eye, eye.add(reach = Vec3d.fromPolar((float)look.pitch(), (float)look.yaw()).multiply(player.getBlockInteractionRange())), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, (Entity)player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static int shade(Box box, Vec3d blast, List<Vec3d> points) {
        int blocked = 0;
        for (Vec3d point : points) {
            if (!box.raycast(point, blast).isPresent()) continue;
            ++blocked;
        }
        return blocked;
    }

    private static List<Vec3d> bodyPoints(Box body) {
        double stepX = 1.0 / ((body.maxX - body.minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((body.maxY - body.minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((body.maxZ - body.minZ) * 2.0 + 1.0);
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
        ArrayList<Vec3d> points = new ArrayList<Vec3d>();
        for (double fx = 0.0; fx <= 1.0; fx += stepX) {
            for (double fy = 0.0; fy <= 1.0; fy += stepY) {
                for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
                    points.add(new Vec3d(MathHelper.lerp((double)fx, (double)body.minX, (double)body.maxX) + offsetX, MathHelper.lerp((double)fy, (double)body.minY, (double)body.maxY), MathHelper.lerp((double)fz, (double)body.minZ, (double)body.maxZ) + offsetZ));
                }
            }
        }
        return points;
    }

        public record Spot(BlockPos pos, BlockPos support, Direction face, Vec3d aim) {
        public boolean standing() {
            return this.support == null;
        }
    }

        private record Cell(BlockPos pos, int shade, double distance) {
    }
}

