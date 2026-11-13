package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BlockListSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Highlights chosen blocks through walls. Chunk sections that cannot contain them are skipped quickly. */
public class BlockESP extends Module {
    public static BlockESP INSTANCE;

    private final BlockListSetting blocks = add(new BlockListSetting("Blocks", Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS));
    private final SliderSetting range = add(new SliderSetting("Range", 48, 8, 128, 1));
    private final SliderSetting delay = add(new SliderSetting("ScanDelay", 20, 5, 100, 1).setSuffix("t"));
    private final SliderSetting maxBlocks = add(new SliderSetting("MaxBlocks", 300, 50, 2000, 10));
    private final ColorSetting fill = add(new ColorSetting("Fill", new Color(0, 200, 255, 50)).injectBoolean(true));
    private final ColorSetting box = add(new ColorSetting("Box", new Color(0, 200, 255, 255)).injectBoolean(true));

    private volatile List<BlockPos> found = Collections.emptyList();
    private int ticks = 0;

    public BlockESP() {
        super("BlockESP", "Highlights chosen blocks through walls", Category.Render);
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return String.valueOf(found.size());
    }

    @Override
    public void onEnable() {
        ticks = 0;
    }

    @Override
    public void onDisable() {
        found = Collections.emptyList();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        if (ticks-- > 0) return;
        ticks = delay.getValueInt();
        scan();
    }

    private void scan() {
        Set<Block> targets = blocks.getBlocks();
        if (targets.isEmpty()) {
            found = Collections.emptyList();
            return;
        }
        double r = range.getValue();
        double r2 = r * r;
        int limit = maxBlocks.getValueInt();
        Vec3d pp = mc.player.getPos();
        int chunkRange = (int) Math.ceil(r / 16.0);
        ChunkPos pc = mc.player.getChunkPos();
        List<BlockPos> list = new ArrayList<>();

        outer:
        for (int cx = pc.x - chunkRange; cx <= pc.x + chunkRange; cx++) {
            for (int cz = pc.z - chunkRange; cz <= pc.z + chunkRange; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection section = sections[i];
                    if (section == null || !section.hasAny(state -> targets.contains(state.getBlock()))) continue;
                    int baseY = chunk.getBottomY() + i * 16;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!targets.contains(state.getBlock())) continue;
                                int wx = cx * 16 + x;
                                int wy = baseY + y;
                                int wz = cz * 16 + z;
                                double dx = wx + 0.5 - pp.x;
                                double dy = wy + 0.5 - pp.y;
                                double dz = wz + 0.5 - pp.z;
                                if (dx * dx + dy * dy + dz * dz > r2) continue;
                                list.add(new BlockPos(wx, wy, wz));
                                if (list.size() >= limit) break outer;
                            }
                        }
                    }
                }
            }
        }
        found = list;
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (mc.world == null) return;
        for (BlockPos pos : found) {
            Render3DUtil.draw3DBox(matrixStack, new Box(pos), fill.getValue(), box.getValue(), box.booleanValue, fill.booleanValue);
        }
    }
}
