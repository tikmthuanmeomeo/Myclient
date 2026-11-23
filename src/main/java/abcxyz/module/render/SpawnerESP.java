package abcxyz.module.render;

import abcxyz.compat.Color;
import abcxyz.compat.Render3DEvent;
import abcxyz.compat.ShapeMode;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.ModeSetting;
import abcxyz.core.Module;
import abcxyz.core.Notify;
import abcxyz.core.NumSetting;
import abcxyz.render.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows what mob every mob spawner around you spawns and marks SMP spawners (made by a plugin: delays at the maximum and
 * no nearby-entity limit) with a tall column through the whole world height.
 * <p>
 * Ported from ArrowAddon (MIT License, (c) Nhân-Tài (ArrowAddon)), see THIRD_PARTY_NOTICES/ArrowAddon-LICENSE.txt.
 * The jump scare of the original is not included.
 */
public class SpawnerESP extends Module {
    private static final int SCAN_INTERVAL = 10;
    private static final int MAX_LABELS = 64;
    private static final double CACHE_RANGE_SQ = 262144.0;

    private final ModeSetting filter = add(new ModeSetting("Filter", "Both", "Both", "Natural", "SMP"));
    private final NumSetting labelScale = add(new NumSetting("LabelScale", 1.0, 0.5, 3.0, 0.1));

    private final BoolSetting useWorldHeight = add(new BoolSetting("UseWorldHeight", true));
    private final NumSetting columnBottomY = add(new NumSetting("ColumnBottomY", -64, -64, 320, 1));
    private final NumSetting columnTopY = add(new NumSetting("ColumnTopY", 320, -64, 320, 1));
    private final NumSetting columnWidth = add(new NumSetting("ColumnWidth", 0.1, 0.05, 1.0, 0.05));
    private final NumSetting renderDistance = add(new NumSetting("RenderDistance", 256, 16, 512, 1));
    private final ModeSetting shape = add(new ModeSetting("Shape", "Both", "Both", "Lines", "Sides"));
    private final BoolSetting renderColumn = add(new BoolSetting("Column", true))
            .with(useWorldHeight, columnBottomY, columnTopY, columnWidth, renderDistance, shape);

    private record Cached(String label, boolean smp) {
    }

    private record Tracked(BlockPos pos, String label, boolean smp) {
    }

    private final Map<BlockPos, Cached> cache = new ConcurrentHashMap<>();
    private final Set<BlockPos> notified = ConcurrentHashMap.newKeySet();
    private volatile List<Tracked> tracked = List.of();
    private int tickCounter = 0;
    private ClientWorld lastWorld = null;

    public SpawnerESP() {
        super("SpawnerESP", "Labels every mob spawner with its mob and marks SMP spawners with a column", Category.BaseFinder);
        labelScale.label = "Label scale";
        useWorldHeight.label = "Whole world height";
        columnBottomY.label = "Bottom Y";
        columnTopY.label = "Top Y";
        columnWidth.label = "Width";
        renderDistance.label = "Render distance";
        renderColumn.label = "SMP column";
    }

    @Override
    public String info() {
        return String.valueOf(tracked.size());
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
        cache.clear();
        notified.clear();
        tracked = List.of();
        tickCounter = 0;
        lastWorld = null;
    }

    private static boolean isSmp(NbtCompound nbt) {
        if (nbt == null) return false;
        short min = nbt.contains("MinSpawnDelay") ? nbt.getShort("MinSpawnDelay") : 0;
        short max = nbt.contains("MaxSpawnDelay") ? nbt.getShort("MaxSpawnDelay") : 0;
        int nearby = nbt.contains("MaxNearbyEntities") ? nbt.getShort("MaxNearbyEntities") : -1;
        return min == Short.MAX_VALUE && max == Short.MAX_VALUE && nearby == 0;
    }

    private static EntityType<?> spawnerType(NbtCompound nbt) {
        if (nbt == null) return null;
        try {
            NbtCompound data = nbt.getCompound("SpawnData");
            String id = data.getCompound("entity").getString("id");
            if (id.isEmpty()) id = data.getString("id");
            if (id.isEmpty()) return null;
            Identifier ident = Identifier.tryParse(id);
            if (ident == null) return null;
            return Registries.ENTITY_TYPE.containsId(ident) ? Registries.ENTITY_TYPE.get(ident) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            if (!tracked.isEmpty() || !cache.isEmpty()) reset();
            return;
        }
        if (lastWorld != mc.world) {
            reset();
            lastWorld = mc.world;
        }
        if (++tickCounter % SCAN_INTERVAL != 0) return;
        try {
            BlockPos playerPos = mc.player.getBlockPos();
            cache.keySet().removeIf(pos -> !mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
                    || pos.getSquaredDistance(playerPos) > CACHE_RANGE_SQ);
            int viewDist = Math.min(32, mc.options.getClampedViewDistance());
            int pcx = playerPos.getX() >> 4;
            int pcz = playerPos.getZ() >> 4;
            List<Tracked> list = new ArrayList<>();
            List<Double> dists = new ArrayList<>();
            for (int cx = pcx - viewDist; cx <= pcx + viewDist; cx++) {
                for (int cz = pcz - viewDist; cz <= pcz + viewDist; cz++) {
                    if (!mc.world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                    WorldChunk chunk = mc.world.getChunk(cx, cz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!(be instanceof MobSpawnerBlockEntity spawner)) continue;
                        BlockPos pos = spawner.getPos().toImmutable();
                        Cached info = cache.computeIfAbsent(pos, p -> {
                            NbtCompound nbt = spawner.createNbt(mc.world.getRegistryManager());
                            boolean smp = isSmp(nbt);
                            EntityType<?> type = spawnerType(nbt);
                            String mob = type != null ? type.getName().getString() : "Unknown";
                            return new Cached((smp ? "[SMP] " : "[Natural] ") + mob, smp);
                        });
                        if (filter.is("Natural") && info.smp() || filter.is("SMP") && !info.smp()) continue;
                        if (info.smp() && notified.add(pos)) {
                            Notify.chat("SpawnerESP", "SMP spawner at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                        }
                        list.add(new Tracked(pos, info.label(), info.smp()));
                    }
                }
            }
            Vec3d pp = mc.player.getPos();
            list.sort(Comparator.comparingDouble(t -> pp.squaredDistanceTo(t.pos().getX() + 0.5, t.pos().getY() + 0.5, t.pos().getZ() + 0.5)));
            tracked = list;
            if (!notified.isEmpty()) {
                Set<BlockPos> active = new HashSet<>();
                for (Tracked t : list) active.add(t.pos());
                notified.retainAll(active);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        if (mc.world == null || mc.player == null) return;
        List<Tracked> list = tracked;
        if (list.isEmpty()) return;
        try {
            if (renderColumn.on()) drawColumns(context, list);
            int drawn = 0;
            for (Tracked t : list) {
                if (drawn++ >= MAX_LABELS) break;
                BlockPos pos = t.pos();
                int color = t.smp() ? 0xFFFF5555 : 0xFF55FF55;
                Render3D.textRows(context, List.of(List.of(new Render3D.Part(t.label(), color))),
                        new Vec3d(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5), labelScale.d());
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawColumns(WorldRenderContext context, List<Tracked> list) {
        double botY = useWorldHeight.on() ? mc.world.getBottomY() : columnBottomY.d();
        double topY = useWorldHeight.on() ? mc.world.getBottomY() + mc.world.getHeight() : columnTopY.d();
        if (topY <= botY) return;
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double maxDist = renderDistance.d();
        double w = columnWidth.d() / 2.0;
        Color side = new Color(255, 50, 50, 80);
        Color line = new Color(255, 50, 50, 255);
        ShapeMode mode = ShapeMode.valueOf(shape.get());
        Render3DEvent event = new Render3DEvent(context);
        Set<Long> rendered = new HashSet<>();
        for (Tracked t : list) {
            if (!t.smp()) continue;
            BlockPos pos = t.pos();
            long key = ((long) pos.getX() << 32) | ((long) pos.getZ() & 0xFFFFFFFFL);
            if (!rendered.add(key)) continue;
            double cx = pos.getX() + 0.5;
            double cz = pos.getZ() + 0.5;
            double dx = cx - px;
            double dz = cz - pz;
            if (dx * dx + dz * dz > maxDist * maxDist) continue;
            event.renderer.box(cx - w, botY, cz - w, cx + w, topY, cz + w, side, line, mode, 0);
        }
        event.flush();
    }
}
