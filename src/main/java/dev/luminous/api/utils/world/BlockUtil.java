package dev.luminous.api.utils.world;

import dev.luminous.Alien;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.mod.modules.impl.combat.AutoWeb;
import dev.luminous.mod.modules.impl.player.AirPlace;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BlockUtil implements Wrapper {
    public static final List<BlockPos> placedPos = new ArrayList<>();

    public static boolean canPlace(BlockPos pos) {
        return canPlace(pos, 1000);
    }

    public static boolean canPlace(BlockPos pos, double distance) {
        if (getPlaceSide(pos, distance) == null) return false;
        if (!canReplace(pos)) return false;
        return !hasEntity(pos, false);
    }

    public static boolean canPlace(BlockPos pos, double distance, boolean ignoreCrystal) {
        if (getPlaceSide(pos, distance) == null) return false;
        if (!canReplace(pos)) return false;
        return !hasEntity(pos, ignoreCrystal);
    }

    public static boolean clientCanPlace(BlockPos pos, boolean ignoreCrystal) {
        if (!canReplace(pos)) return false;
        return !hasEntity(pos, ignoreCrystal);
    }

    public static List<Entity> getEntities(Box box) {
        List<Entity> list = new ArrayList<>();
        for (Entity entity : Alien.THREAD.getEntities()) {
            if (entity == null) continue;
            if (entity instanceof ArmorStandEntity && AntiCheat.INSTANCE.ignoreArmorStand.getValue()) continue;
            if (entity.getBoundingBox().intersects(box)) {
                list.add(entity);
            }
        }
        return list;
    }

    public static List<EndCrystalEntity> getEndCrystals(Box box) {
        List<EndCrystalEntity> list = new ArrayList<>();
        for (Entity entity : Alien.THREAD.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal) {
                if (crystal.getBoundingBox().intersects(box)) {
                    list.add(crystal);
                }
            }
        }
        return list;
    }

    public static boolean hasEntity(BlockPos pos, boolean ignoreCrystal) {
        return hasEntity(new Box(pos), ignoreCrystal);
    }

    public static boolean hasEntity(Box box, boolean ignoreCrystal) {
        for (Entity entity : getEntities(box)) {
            if (!entity.isAlive() || entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity || entity instanceof ExperienceBottleEntity || entity instanceof ArrowEntity || ignoreCrystal && entity instanceof EndCrystalEntity && mc.player.getEyePos().distanceTo(MathUtil.getClosestPoint(entity)) <= AntiCheat.INSTANCE.ieRange.getValue())
                continue;
            return true;
        }
        return false;
    }

    public static boolean hasCrystal(BlockPos pos) {
        for (Entity entity : getEndCrystals(new Box(pos))) {
            if (!entity.isAlive() || !(entity instanceof EndCrystalEntity))
                continue;
            return true;
        }
        return false;
    }

    public static boolean noEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal) {
        return noEntityBlockCrystal(pos, ignoreCrystal, false);
    }

    public static boolean noEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        for (Entity entity : getEntities(new Box(pos))) {
            if (!entity.isAlive() || ignoreItem && entity instanceof ItemEntity || ignoreCrystal && entity instanceof EndCrystalEntity && mc.player.getEyePos().distanceTo(MathUtil.getClosestPoint(entity)) <= AntiCheat.INSTANCE.ieRange.getValue())
                continue;
            return false;
        }
        return true;
    }

    public static boolean canPlaceCrystal(BlockPos pos) {
        BlockPos obsPos = pos.down();
        BlockPos boost = obsPos.up();
        return (getBlock(obsPos) == Blocks.BEDROCK || getBlock(obsPos) == Blocks.OBSIDIAN)
                && getClickSideStrict(obsPos) != null
                && (mc.world.isAir(boost))
                && noEntityBlockCrystal(boost, false)
                && noEntityBlockCrystal(boost.up(), false)
                && (!ClientSetting.INSTANCE.lowVersion.getValue() || mc.world.isAir(boost.up()));
    }

    public static void placeCrystal(BlockPos pos, boolean rotate) {
        boolean offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
        BlockPos obsPos = pos.down();
        Direction facing = getClickSide(obsPos);
        Vec3d vec = obsPos.toCenterPos().add(facing.getVector().getX() * 0.5, facing.getVector().getY() * 0.5, facing.getVector().getZ() * 0.5);
        if (rotate) {
            Alien.ROTATION.lookAt(vec);
        }
        clickBlock(obsPos, facing, false, offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
    }

    public static void placeBlock(BlockPos pos, boolean rotate) {
        placeBlock(pos, rotate, AntiCheat.INSTANCE.packetPlace.getValue());
    }

    public static void placeBlock(BlockPos pos, boolean rotate, boolean packet) {
        if (allowAirPlace()) {
            placedPos.add(pos);
            airPlace(pos, rotate, Hand.MAIN_HAND, packet);
            return;
        }
        Direction side = getPlaceSide(pos);
        if (side == null) return;
        placedPos.add(pos);
        clickBlock(pos.offset(side), side.getOpposite(), rotate, Hand.MAIN_HAND, packet);
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate) {
        clickBlock(pos, side, rotate, Hand.MAIN_HAND);
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate, Hand hand) {
        clickBlock(pos, side, rotate, hand, AntiCheat.INSTANCE.packetPlace.getValue());
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate, boolean packet) {
        clickBlock(pos, side, rotate, Hand.MAIN_HAND, packet);
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate, Hand hand, boolean packet) {
        Vec3d directionVec = new Vec3d(pos.getX() + 0.5 + side.getVector().getX() * 0.5, pos.getY() + 0.5 + side.getVector().getY() * 0.5, pos.getZ() + 0.5 + side.getVector().getZ() * 0.5);
        if (rotate) {
            Alien.ROTATION.lookAt(directionVec);
        }
        EntityUtil.swingHand(hand, AntiCheat.INSTANCE.interactSwing.getValue());
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        if (packet) {
            Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(hand, result, id));
        } else {
            mc.interactionManager.interactBlock(mc.player, hand, result);
        }
        mc.itemUseCooldown = 4;
        if (rotate) {
            Alien.ROTATION.snapBack();
        }
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate, Hand hand, SwingSide swingSide) {
        Vec3d directionVec = new Vec3d(pos.getX() + 0.5 + side.getVector().getX() * 0.5, pos.getY() + 0.5 + side.getVector().getY() * 0.5, pos.getZ() + 0.5 + side.getVector().getZ() * 0.5);
        if (rotate) {
            Alien.ROTATION.lookAt(directionVec);
        }
        EntityUtil.swingHand(hand, swingSide);
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(hand, result, id));
        mc.itemUseCooldown = 4;
        if (rotate) {
            Alien.ROTATION.snapBack();
        }
    }

    public static void airPlace(BlockPos pos, boolean rotate) {
        airPlace(pos, rotate, Hand.MAIN_HAND, AntiCheat.INSTANCE.packetPlace.getValue());
    }

    public static void airPlace(BlockPos pos, boolean rotate, Hand hand, boolean packet) {
        boolean bypass = hand == Hand.MAIN_HAND && AirPlace.INSTANCE.grimBypass.getValue();
        if (bypass) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, new BlockPos(0, 0, 0), Direction.DOWN));
            hand = Hand.OFF_HAND;
        }
        Direction side = BlockUtil.getClickSide(pos);
        Vec3d directionVec = new Vec3d(pos.getX() + 0.5 + side.getVector().getX() * 0.5, pos.getY() + 0.5 + side.getVector().getY() * 0.5, pos.getZ() + 0.5 + side.getVector().getZ() * 0.5);
        if (rotate) {
            Alien.ROTATION.lookAt(directionVec);
        }
        EntityUtil.swingHand(hand, AntiCheat.INSTANCE.interactSwing.getValue());
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        if (packet) {
            Hand finalHand = hand;
            Module.sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(finalHand, result, id));
        } else {
            mc.interactionManager.interactBlock(mc.player, hand, result);
        }
        mc.itemUseCooldown = 4;
        if (rotate) {
            Alien.ROTATION.snapBack();
        }
        if (bypass) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, new BlockPos(0, 0, 0), Direction.DOWN));
        }
    }

    public static double distanceToXZ(final double x, final double z, double x2, double z2) {
        final double dx = x2 - x;
        final double dz = z2 - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double distanceToXZ(final double x, final double z) {
        return distanceToXZ(x, z, mc.player.getX(), mc.player.getZ());
    }

    public static Direction getPlaceSide(BlockPos pos) {
        if (allowAirPlace()) return BlockUtil.getClickSide(pos);
        double minDistance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction i : Direction.values()) {
            if (!canClick(pos.offset(i))) continue;
            if (canReplace(pos.offset(i))) continue;
            if (!isStrictDirection(pos.offset(i), i.getOpposite())) continue;
            double vecDis = mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos().add(i.getVector().getX() * 0.5, i.getVector().getY() * 0.5, i.getVector().getZ() * 0.5));
            if (vecDis > minDistance) {
                continue;
            }
            side = i;
            minDistance = vecDis;
        }
        return side;
    }
    public static Direction getBestNeighboring(BlockPos pos, Direction facing) {
        Direction bestFacing = null;
        double distance = 0;
        for (Direction i : Direction.values()) {
            if (facing != null && pos.offset(i).equals(pos.offset(facing, -1)) || i == Direction.DOWN) continue;
            if (getPlaceSide(pos) != null) {
                if (bestFacing == null || mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos()) < distance) {
                    bestFacing = i;
                    distance = mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos());
                }
            }
        }
        return bestFacing;
    }
    public static Direction getPlaceSide(BlockPos pos, double reachDistance) {
        if (allowAirPlace()) {
            Direction i = getClickSide(pos);
            double vecDis = mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos().add(i.getVector().getX() * 0.5, i.getVector().getY() * 0.5, i.getVector().getZ() * 0.5));
            if (Math.sqrt(vecDis) > reachDistance) {
                return null;
            }
            return Direction.DOWN;
        }
        double minDistance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction i : Direction.values()) {
            if (!canClick(pos.offset(i))) continue;
            if (canReplace(pos.offset(i))) continue;
            if (!isStrictDirection(pos.offset(i), i.getOpposite())) continue;
            double vecDis = mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos().add(i.getVector().getX() * 0.5, i.getVector().getY() * 0.5, i.getVector().getZ() * 0.5));
            if (Math.sqrt(vecDis) > reachDistance) {
                continue;
            }
            if (vecDis > minDistance) {
                continue;
            }
            side = i;
            minDistance = vecDis;
        }
        return side;
    }

    public static Direction getClickSide(BlockPos pos) {
        Direction side = Direction.UP;
        double minDistance = Double.MAX_VALUE;
        for (Direction i : Direction.values()) {
            if (!isStrictDirection(pos, i)) continue;
            double disSq = mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos());
            if (disSq > minDistance)
                continue;
            side = i;
            minDistance = disSq;
        }
        return side;
    }

    public static Direction getClickSideStrict(BlockPos pos) {
        Direction side = null;
        double minDistance = Double.MAX_VALUE;
        for (Direction i : Direction.values()) {
            if (!isStrictDirection(pos, i)) continue;
            double disSq = mc.player.getEyePos().squaredDistanceTo(pos.offset(i).toCenterPos());
            if (disSq > minDistance)
                continue;
            side = i;
            minDistance = disSq;
        }
        return side;
    }

    public static boolean isStrictDirection(BlockPos pos, Direction side, double reachDistance) {
        double vecDis = mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos().add(side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5));
        if (Math.sqrt(vecDis) > reachDistance) {
            return false;
        }
        return isStrictDirection(pos, side);
    }

    public static boolean isStrictDirection(BlockPos pos, Direction side) {
        switch (AntiCheat.INSTANCE.placement.getValue()) {
            case Vanilla -> {
                return true;
            }
            case Legit -> {
                return EntityUtil.canSee(pos, side);
            }
            case Grim -> {
                return grimStrictDirectionCheck(pos, side, mc.world, mc.player);
            }
            case NCP -> {
                if (mc.world.getBlockState(pos.offset(side)).isFullCube(mc.world, pos.offset(side)))
                    return false;

                Vec3d eyePos = mc.player.getEyePos();
                Vec3d blockCenter = pos.toCenterPos();
                ArrayList<Direction> validAxis = new ArrayList<>();
                validAxis.addAll(checkAxis(eyePos.x - blockCenter.x, Direction.WEST, Direction.EAST, false));
                validAxis.addAll(checkAxis(eyePos.y - blockCenter.y, Direction.DOWN, Direction.UP, true));
                validAxis.addAll(checkAxis(eyePos.z - blockCenter.z, Direction.NORTH, Direction.SOUTH, false));
                return validAxis.contains(side);
            }
        }
        return true;
    }

    private static final double MIN_EYE_HEIGHT = 0.4;
    private static final double MAX_EYE_HEIGHT = 1.62;
    private static final double MOVEMENT_THRESHOLD = 0.0002;

    public static boolean grimStrictDirectionCheck(BlockPos pos, Direction direction, ClientWorld level, ClientPlayerEntity player) {
        // see ac.grim.grimac.checks.impl.scaffolding.PositionPlace
        Box combined = getCombinedBox(pos, level);
        Box eyePositions = new Box(player.getX(), player.getY() + MIN_EYE_HEIGHT, player.getZ(), player.getX(), player.getY() + MAX_EYE_HEIGHT, player.getZ()).expand(MOVEMENT_THRESHOLD);
        if (isIntersected(eyePositions, combined)) {
            return true;
        }

        return !switch (direction) {
            case NORTH -> eyePositions.minZ > combined.minZ;
            case SOUTH -> eyePositions.maxZ < combined.maxZ;
            case EAST -> eyePositions.maxX < combined.maxX;
            case WEST -> eyePositions.minX > combined.minX;
            case UP -> eyePositions.maxY < combined.maxY;
            case DOWN -> eyePositions.minY > combined.minY;
        };
    }

    private static Box getCombinedBox(BlockPos pos, World level) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos).offset(pos.getX(), pos.getY(), pos.getZ());
        Box combined = new Box(pos);
        for (Box box : shape.getBoundingBoxes()) {
            double minX = Math.max(box.minX, combined.minX);
            double minY = Math.max(box.minY, combined.minY);
            double minZ = Math.max(box.minZ, combined.minZ);
            double maxX = Math.min(box.maxX, combined.maxX);
            double maxY = Math.min(box.maxY, combined.maxY);
            double maxZ = Math.min(box.maxZ, combined.maxZ);
            combined = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return combined;
    }

    private static boolean isIntersected(Box bb, Box other) {
        return other.maxX - VoxelShapes.MIN_SIZE > bb.minX
                && other.minX + VoxelShapes.MIN_SIZE < bb.maxX
                && other.maxY - VoxelShapes.MIN_SIZE > bb.minY
                && other.minY + VoxelShapes.MIN_SIZE < bb.maxY
                && other.maxZ - VoxelShapes.MIN_SIZE > bb.minZ
                && other.minZ + VoxelShapes.MIN_SIZE < bb.maxZ;
    }

    public static ArrayList<Direction> checkAxis(double diff, Direction negativeSide, Direction positiveSide, boolean vertical) {
        ArrayList<Direction> valid = new ArrayList<>();
        if (vertical) {
            if (diff < -0.5) {
                valid.add(negativeSide);
            }
            if (AntiCheat.INSTANCE.upDirectionLimit.getValue()) {
                if (diff > 0.5) {
                    valid.add(positiveSide);
                }
            } else {
                if (diff > -0.5) {
                    valid.add(positiveSide);
                }
            }
        } else {
            if (diff < -0.5) {
                valid.add(negativeSide);
            }
            if (diff > 0.5) {
                valid.add(positiveSide);
            }
        }
        return valid;
    }

    public static ArrayList<BlockEntity> getTileEntities() {
        return getLoadedChunks().flatMap(chunk -> chunk.getBlockEntities().values().stream()).collect(Collectors.toCollection(ArrayList::new));
    }

    public static Stream<WorldChunk> getLoadedChunks() {
        int radius = Math.max(2, mc.options.getClampedViewDistance()) + 3;
        int diameter = radius * 2 + 1;

        ChunkPos center = mc.player.getChunkPos();
        ChunkPos min = new ChunkPos(center.x - radius, center.z - radius);
        ChunkPos max = new ChunkPos(center.x + radius, center.z + radius);

        return Stream.iterate(min, pos -> {
                    int x = pos.x;
                    int z = pos.z;
                    x++;

                    if (x > max.x) {
                        x = min.x;
                        z++;
                    }

                    return new ChunkPos(x, z);

                }).limit((long) diameter * diameter)
                .filter(c -> mc.world.isChunkLoaded(c.x, c.z))
                .map(c -> mc.world.getChunk(c.x, c.z)).filter(Objects::nonNull);
    }

    public static ArrayList<BlockPos> getSphere(float range) {
        return getSphere(range, mc.player.getEyePos());
    }

    public static BlockPos getBlock(Block block, float range) {
        for (BlockPos pos : getSphere(range)) {
            if (mc.world.getBlockState(pos).getBlock() == block) return pos;
        }
        return null;
    }

    public static BlockPos getBlock(Class<?> block, float range) {
        for (BlockPos pos : getSphere(range)) {
            if (block.isInstance(mc.world.getBlockState(pos).getBlock())) return pos;
        }
        return null;
    }

    public static ArrayList<BlockPos> getSphere(float range, Vec3d pos) {
        ArrayList<BlockPos> list = new ArrayList<>();
        for (double y = pos.getY() + range; y > pos.getY() - range; --y) {
            if (y < -64) continue;
            for (double x = pos.getX() - range; x < pos.getX() + range; ++x) {
                for (double z = pos.getZ() - range; z < pos.getZ() + range; ++z) {
                    BlockPos curPos = new BlockPosX(x, y, z);
                    if (curPos.toCenterPos().distanceTo(pos) > range) continue;
                    list.add(curPos);
                }
            }
        }
        return list;
    }

    public static Block getBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock();
    }

    public static boolean canReplace(BlockPos pos) {
        if (pos.getY() >= 320) return false;
        if (AntiCheat.INSTANCE.multiPlace.getValue() && placedPos.contains(pos)) {
            return false;
        }
        BlockState state = mc.world.getBlockState(pos);
        if (state.getBlock() == Blocks.COBWEB) {
            if (AutoWeb.ignore && AutoCrystal.INSTANCE.replace.getValue()) return true;
        }
        return state.isReplaceable();
    }

    public static boolean canClick(BlockPos pos) {
        if (AntiCheat.INSTANCE.multiPlace.getValue() && placedPos.contains(pos)) {
            return true;
        }
        BlockState state = mc.world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.COBWEB) {
            if (AutoWeb.ignore) {
                return AutoCrystal.INSTANCE.airPlace.getValue();
            }
        }
        return mc.player.isSneaking() || !isClickable(block);
    }

    public static boolean isClickable(Block block) {
        return block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock
                || block instanceof ButtonBlock
                || block instanceof AbstractPressurePlateBlock
                || block instanceof BlockWithEntity
                || block instanceof BedBlock
                || block instanceof FenceGateBlock
                || block instanceof DoorBlock
                || block instanceof NoteBlock
                || block instanceof TrapdoorBlock;
    }

    public static boolean canCollide(Box box) {
        return canCollide(mc.player, box);
    }

    public static boolean canCollide(@Nullable Entity entity, Box box) {
        BlockCollisionSpliterator<VoxelShape> blockCollisionSpliterator = new BlockCollisionSpliterator<>(mc.world, entity, box, false, (pos, voxelShape) -> voxelShape);

        do {
            if (!blockCollisionSpliterator.hasNext()) {
                return false;
            }
        } while (blockCollisionSpliterator.next().isEmpty());

        return true;
    }

    public static boolean allowAirPlace() {
        return AirPlace.INSTANCE.isOn() && AirPlace.INSTANCE.module.getValue();
    }
}
