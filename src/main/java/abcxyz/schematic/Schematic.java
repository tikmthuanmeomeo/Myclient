package abcxyz.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A loaded schematic. Block positions are relative to the minimum corner of the schematic (0, 0, 0).
 * Supports Litematica (.litematic) and vanilla structure (.nbt) files.
 */
public class Schematic {
    public final String name;
    private final Map<Long, BlockState> blocks = new HashMap<>();
    public int sizeX;
    public int sizeY;
    public int sizeZ;

    private record Entry(int x, int y, int z, BlockState state) {
    }

    private Schematic(String name) {
        this.name = name;
    }

    public BlockState get(BlockPos relative) {
        return blocks.get(relative.asLong());
    }

    public int blockCount() {
        return blocks.size();
    }

    public static Schematic load(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        List<Entry> entries = new ArrayList<>();
        if (fileName.toLowerCase().endsWith(".litematic")) {
            readLitematic(root, entries);
        } else {
            readStructure(root, entries);
        }
        if (entries.isEmpty()) throw new IOException("the file has no blocks");

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Entry e : entries) {
            minX = Math.min(minX, e.x());
            minY = Math.min(minY, e.y());
            minZ = Math.min(minZ, e.z());
            maxX = Math.max(maxX, e.x());
            maxY = Math.max(maxY, e.y());
            maxZ = Math.max(maxZ, e.z());
        }
        Schematic schematic = new Schematic(fileName);
        schematic.sizeX = maxX - minX + 1;
        schematic.sizeY = maxY - minY + 1;
        schematic.sizeZ = maxZ - minZ + 1;
        for (Entry e : entries) {
            schematic.blocks.put(BlockPos.asLong(e.x() - minX, e.y() - minY, e.z() - minZ), e.state());
        }
        return schematic;
    }

    private static BlockState readState(NbtCompound compound) {
        return NbtHelper.toBlockState(Registries.BLOCK, compound);
    }

    private static void readLitematic(NbtCompound root, List<Entry> out) throws IOException {
        NbtCompound regions = root.getCompound("Regions");
        for (String key : regions.getKeys()) {
            NbtCompound region = regions.getCompound(key);
            NbtCompound pos = region.getCompound("Position");
            NbtCompound size = region.getCompound("Size");
            int sx = size.getInt("x");
            int sy = size.getInt("y");
            int sz = size.getInt("z");
            int ax = Math.abs(sx);
            int ay = Math.abs(sy);
            int az = Math.abs(sz);
            int minX = pos.getInt("x") + (sx < 0 ? sx + 1 : 0);
            int minY = pos.getInt("y") + (sy < 0 ? sy + 1 : 0);
            int minZ = pos.getInt("z") + (sz < 0 ? sz + 1 : 0);

            NbtList palette = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            if (palette.isEmpty()) continue;
            BlockState[] states = new BlockState[palette.size()];
            for (int i = 0; i < states.length; i++) {
                states[i] = readState(palette.getCompound(i));
            }
            long[] data = region.getLongArray("BlockStates");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long total = (long) ax * ay * az;
            if ((total * bits + 63) / 64 > data.length) throw new IOException("block data is shorter than the region size");
            for (int y = 0; y < ay; y++) {
                for (int z = 0; z < az; z++) {
                    for (int x = 0; x < ax; x++) {
                        long index = ((long) y * az + z) * ax + x;
                        int id = readPacked(data, bits, index);
                        if (id < 0 || id >= states.length) continue;
                        BlockState state = states[id];
                        if (state.isAir()) continue;
                        out.add(new Entry(minX + x, minY + y, minZ + z, state));
                    }
                }
            }
        }
    }

    private static int readPacked(long[] data, int bits, long index) {
        long bitIndex = index * bits;
        int startLong = (int) (bitIndex >> 6);
        int startOffset = (int) (bitIndex & 63);
        long mask = (1L << bits) - 1L;
        int endLong = (int) (((index + 1) * bits - 1) >> 6);
        if (startLong == endLong) {
            return (int) ((data[startLong] >>> startOffset) & mask);
        }
        int endOffset = 64 - startOffset;
        return (int) (((data[startLong] >>> startOffset) | (data[endLong] << endOffset)) & mask);
    }

    private static void readStructure(NbtCompound root, List<Entry> out) throws IOException {
        NbtList palette = root.getList("palette", NbtElement.COMPOUND_TYPE);
        NbtList blockList = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        if (palette.isEmpty() || blockList.isEmpty()) throw new IOException("not a structure file");
        BlockState[] states = new BlockState[palette.size()];
        for (int i = 0; i < states.length; i++) {
            states[i] = readState(palette.getCompound(i));
        }
        for (int i = 0; i < blockList.size(); i++) {
            NbtCompound block = blockList.getCompound(i);
            NbtList pos = block.getList("pos", NbtElement.INT_TYPE);
            int id = block.getInt("state");
            if (pos.size() < 3 || id < 0 || id >= states.length) continue;
            BlockState state = states[id];
            if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID)) continue;
            out.add(new Entry(pos.getInt(0), pos.getInt(1), pos.getInt(2), state));
        }
    }
}
