package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.KeySetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.render.Render3D;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Finds probable base locations from what grows in a chunk: kelp, vines, bamboo, cocoa, amethyst and bee nests that
 * are fully grown, plus rotated deepslate (placed by a player). Chunks with enough "growth points" are painted and
 * grouped into regions with a ring around them.
 * <p>
 * Ported from ArrowAddon (MIT License, (c) Nhân-Tài (ArrowAddon)), see THIRD_PARTY_NOTICES/ArrowAddon-LICENSE.txt.
 */
public class SusChunkFinder extends Module {
    private static final int DEFAULT_SENSITIVITY = 4;
    private static final double RENDER_Y = 60.0;
    private static final int MAX_TRACERS = 64;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final NumSetting simDistance = add(new NumSetting("SimDistance", 3, 1, 32, 1));
    private final NumSetting sensitivity = add(new NumSetting("Sensitivity", DEFAULT_SENSITIVITY, 1, 100, 1));
    private final NumSetting autoRange = add(new NumSetting("AutoRange", 10, 1, 32, 1));
    private final BoolSetting autoSensitivity = add(new BoolSetting("AutoSensitivity", true)).with(autoRange);
    private final KeySetting restoreKey = add(new KeySetting("RestoreKey", -1));

    private final BoolSetting kelp = add(new BoolSetting("Kelp", true));
    private final BoolSetting caveVine = add(new BoolSetting("CaveVine", true));
    private final BoolSetting vines = add(new BoolSetting("Vines", true));
    private final BoolSetting amethyst = add(new BoolSetting("Amethyst", true));
    private final BoolSetting bamboo = add(new BoolSetting("Bamboo", true));
    private final BoolSetting cocoa = add(new BoolSetting("Cocoa", true));
    private final BoolSetting beeNest = add(new BoolSetting("BeeNest", true));
    private final BoolSetting rotatedDeepslate = add(new BoolSetting("RotatedDeepslate", true));
    private final NumSetting maxHoles = add(new NumSetting("MaxHoles", 2, 1, 10, 1));
    private final BoolSetting lowHole = add(new BoolSetting("LowHole", false)).with(maxHoles);

    private final NumSetting fillOpacity = add(new NumSetting("FillOpacity", 32, 0, 255, 1));
    private final NumSetting lineOpacity = add(new NumSetting("LineOpacity", 210, 0, 255, 1));
    private final NumSetting ringSegments = add(new NumSetting("RingSegments", 96, 8, 256, 1));
    private final BoolSetting rainbow = add(new BoolSetting("Rainbow", false));
    private final BoolSetting animation = add(new BoolSetting("Animation", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracers", false));

    private final Map<ChunkPos, Result> results = new ConcurrentHashMap<>();
    private final Set<ChunkPos> scanned = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<ChunkPos> announced = ConcurrentHashMap.newKeySet();
    private volatile Set<ChunkPos> painted = Collections.emptySet();
    private volatile List<double[]> regions = Collections.emptyList();

    private ExecutorService executor;
    private ClientWorld lastWorld;
    private int tickCounter;
    private int autoBoost = 0;
    private boolean restoreWasDown = false;

    public SusChunkFinder() {
        super("SusChunkFinder", "Finds probable bases from fully grown plants and rotated deepslate", Category.Render);
        simDistance.label = "Scan distance (chunks)";
        sensitivity.label = "Sensitivity";
        autoSensitivity.label = "Auto sensitivity";
        autoRange.label = "Auto range (chunks)";
        restoreKey.label = "Restore sensitivity key";
        caveVine.label = "Cave vine";
        beeNest.label = "Bee nest";
        rotatedDeepslate.label = "Rotated deepslate";
        lowHole.label = "Low hole";
        maxHoles.label = "Max holes";
        fillOpacity.label = "Fill opacity";
        lineOpacity.label = "Line opacity";
        ringSegments.label = "Ring segments";
        // a chunk that just arrived from the server is scanned right away
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!isEnabled()) return;
            scanned.remove(chunk.getPos());
            submitScan(chunk, true);
        });
    }

    @Override
    public String info() {
        return String.valueOf(painted.size());
    }

    private void restoreSensitivity() {
        autoBoost = 0;
        sensitivity.set((double) DEFAULT_SENSITIVITY);
        notice("Sensitivity restored to " + DEFAULT_SENSITIVITY + ".");
    }

    private void notice(String text) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[SusChunkFinder] " + text), false);
    }

    private int need() {
        return sensitivity.i() + (autoSensitivity.on() ? autoBoost : 0);
    }

    @Override
    public void onEnable() {
        clearState();
        autoBoost = 0;
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "SusChunkFinder-scan");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onDisable() {
        if (executor != null && !executor.isShutdown()) executor.shutdownNow();
        executor = null;
        clearState();
    }

    private void clearState() {
        results.clear();
        scanned.clear();
        inFlight.clear();
        announced.clear();
        painted = Collections.emptySet();
        regions = Collections.emptyList();
        lastWorld = null;
    }

    private WorldChunk chunkAt(int x, int z) {
        return mc.world.getChunkManager().getWorldChunk(x, z);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        boolean down = restoreKey.key() >= 0 && mc.currentScreen == null
                && InputUtil.isKeyPressed(mc.getWindow().getHandle(), restoreKey.key());
        if (down && !restoreWasDown) restoreSensitivity();
        restoreWasDown = down;

        if (lastWorld != mc.world) { // another world: forget everything
            clearState();
            lastWorld = mc.world;
        }
        if (++tickCounter % 5 != 0) return;

        ChunkPos c = mc.player.getChunkPos();
        // the chunks right around you are re-scanned all the time
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                WorldChunk chunk = chunkAt(c.x + dx, c.z + dz);
                if (chunk != null) submitScan(chunk, true);
            }
        }
        if (tickCounter % 10 != 0) return;

        results.keySet().removeIf(p -> chunkAt(p.x, p.z) == null);
        scanned.removeIf(p -> chunkAt(p.x, p.z) == null);

        // every loaded chunk gets scanned once
        int r = mc.options.getViewDistance().getValue() + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                ChunkPos p = new ChunkPos(c.x + dx, c.z + dz);
                if (scanned.contains(p)) continue;
                WorldChunk chunk = chunkAt(p.x, p.z);
                if (chunk != null) submitScan(chunk, false);
            }
        }
        if (tickCounter % 20 == 0) {
            int rr = Math.min(6, Math.max(5, simDistance.i()));
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    WorldChunk chunk = chunkAt(c.x + dx, c.z + dz);
                    if (chunk != null) submitScan(chunk, true);
                }
            }
        }
        rebuild();
        if (autoSensitivity.on() && tickCounter % 40 == 0) autoAdjust(c);
    }

    private void autoAdjust(ChunkPos me) {
        int range = autoRange.i();
        int count = 0;
        for (ChunkPos p : painted) {
            if (Math.max(Math.abs(p.x - me.x), Math.abs(p.z - me.z)) <= range) count++;
        }
        if (count > 5 && autoBoost < 10) autoBoost++;
        else if (count == 0 && autoBoost > 0) autoBoost--;
    }

    private boolean[] enabled() {
        return new boolean[]{kelp.on(), caveVine.on(), vines.on(), amethyst.on(), bamboo.on(), beeNest.on(), rotatedDeepslate.on(), cocoa.on()};
    }

    private void submitScan(WorldChunk chunk, boolean force) {
        ExecutorService ex = executor;
        if (ex == null || ex.isShutdown()) return;
        ChunkPos pos = chunk.getPos();
        if (!force && scanned.contains(pos)) return;
        if (!inFlight.add(pos)) return;
        scanned.add(pos);
        boolean[] on = enabled();
        boolean holes = lowHole.on();
        try {
            ex.submit(() -> {
                try {
                    Result res = scan(chunk, on, holes);
                    if (heatOf(res) > 0) results.put(pos, res);
                    else results.remove(pos);
                } catch (Throwable t) {
                    scanned.remove(pos);
                } finally {
                    inFlight.remove(pos);
                }
            });
        } catch (Throwable t) {
            scanned.remove(pos);
            inFlight.remove(pos);
        }
    }

    private int heatOf(Result r) {
        int heat = (r.hasUngrown() ? 0 : r.heat()) + r.rotated().size() * 4;
        if (lowHole.on() && r.holes() >= 1 && r.holes() <= maxHoles.i()) heat++;
        return heat;
    }

    /** Picks the chunks that are hot enough and joins neighbours into regions (centre + radius). */
    private void rebuild() {
        if (mc.world == null) return;
        int need = need();
        HashSet<ChunkPos> out = new HashSet<>();
        for (Map.Entry<ChunkPos, Result> e : results.entrySet()) {
            if (heatOf(e.getValue()) < need) continue;
            ChunkPos p = e.getKey();
            if (chunkAt(p.x, p.z) == null) continue;
            out.add(p);
        }
        if (out.isEmpty()) {
            painted = Collections.emptySet();
            regions = Collections.emptyList();
            return;
        }
        ArrayList<double[]> reg = new ArrayList<>();
        HashSet<ChunkPos> seen = new HashSet<>();
        for (ChunkPos start : out) {
            if (!seen.add(start)) continue;
            ArrayDeque<ChunkPos> q = new ArrayDeque<>();
            q.add(start);
            ArrayList<ChunkPos> members = new ArrayList<>();
            double sx = 0.0, sz = 0.0;
            while (!q.isEmpty()) {
                ChunkPos c = q.poll();
                members.add(c);
                sx += c.getStartX() + 8;
                sz += c.getStartZ() + 8;
                for (int[] d : DIRS) {
                    ChunkPos nb = new ChunkPos(c.x + d[0], c.z + d[1]);
                    if (out.contains(nb) && seen.add(nb)) q.add(nb);
                }
            }
            double cx = sx / members.size();
            double cz = sz / members.size();
            double radius = 8.0;
            for (ChunkPos m : members) {
                double dx = m.getStartX() + 8 - cx;
                double dz = m.getStartZ() + 8 - cz;
                radius = Math.max(radius, Math.sqrt(dx * dx + dz * dz) + 10.0);
            }
            reg.add(new double[]{cx, cz, radius});
            boolean fresh = true;
            for (ChunkPos m : members) {
                if (announced.contains(m)) {
                    fresh = false;
                    break;
                }
            }
            if (!fresh) continue;
            announced.addAll(members);
            notice("Sus chunk at X: " + Math.round(cx) + " Z: " + Math.round(cz));
        }
        painted = out;
        regions = reg;
    }

    // ---------------------------------------------------------------- scanning (runs on a worker thread)

    private static Result scan(WorldChunk chunk, boolean[] on, boolean holeCheck) {
        int heat = 0;
        int young = 0;
        ArrayList<BlockPos> rotated = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomY();
        BlockPos.Mutable mp = new BlockPos.Mutable();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection s = sections[i];
            if (s == null || !s.hasAny(st -> relevant(st.getBlock(), on))) continue;
            int baseY = bottom + i * 16;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState st = s.getBlockState(x, y, z);
                        Block b = st.getBlock();
                        int grow = growth(b, st, chunk, on, startX + x, baseY + y, startZ + z, mp);
                        if (grow == 1) heat++;
                        else if (grow == -1) young++;
                        else if (grow == 2) rotated.add(new BlockPos(startX + x, baseY + y, startZ + z));
                    }
                }
            }
        }
        int holes = holeCheck ? scanLowHoles(chunk) : 0;
        return new Result(heat, young > heat, rotated, holes);
    }

    private static boolean relevant(Block b, boolean[] on) {
        return on[0] && b == Blocks.KELP
                || on[1] && b == Blocks.CAVE_VINES
                || on[2] && b == Blocks.VINE
                || on[3] && (b == Blocks.AMETHYST_CLUSTER || b == Blocks.SMALL_AMETHYST_BUD
                || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.LARGE_AMETHYST_BUD)
                || on[4] && b == Blocks.BAMBOO
                || on[5] && b == Blocks.BEE_NEST
                || on[6] && b == Blocks.DEEPSLATE
                || on[7] && b == Blocks.COCOA;
    }

    /** 1 = fully grown, -1 = still young, 2 = rotated deepslate, 0 = not interesting. */
    private static int growth(Block b, BlockState st, WorldChunk chunk, boolean[] on, int wx, int wy, int wz, BlockPos.Mutable mp) {
        if (on[0] && b == Blocks.KELP) {
            return st.contains(Properties.AGE_25) && st.get(Properties.AGE_25) >= 25 ? 1 : -1;
        }
        if (on[1] && b == Blocks.CAVE_VINES) {
            return st.contains(Properties.AGE_25) && st.get(Properties.AGE_25) >= 25 ? 1 : -1;
        }
        if (on[4] && b == Blocks.BAMBOO) {
            return st.contains(Properties.AGE_1) && st.get(Properties.AGE_1) >= 1 ? 1 : -1;
        }
        if (on[5] && b == Blocks.BEE_NEST) {
            return st.contains(Properties.HONEY_LEVEL) && st.get(Properties.HONEY_LEVEL) >= 5 ? 1 : -1;
        }
        if (on[7] && b == Blocks.COCOA) {
            return st.contains(Properties.AGE_2) && st.get(Properties.AGE_2) >= 2 ? 1 : -1;
        }
        if (on[3]) {
            if (b == Blocks.AMETHYST_CLUSTER) return 1;
            if (b == Blocks.SMALL_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.LARGE_AMETHYST_BUD) return -1;
        }
        if (on[2] && b == Blocks.VINE) {
            int len = 1;
            for (int k = 1; k <= 5 && chunk.getBlockState(mp.set(wx, wy + k, wz)).getBlock() == Blocks.VINE; k++) len++;
            return len >= 5 ? 1 : 0;
        }
        if (on[6] && b == Blocks.DEEPSLATE && st.contains(Properties.AXIS) && st.get(Properties.AXIS) != Direction.Axis.Y) {
            return 2;
        }
        return 0;
    }

    /** Counts vertical 1x1 shafts in the lowest layers (man made holes down to bedrock level). */
    private static int scanLowHoles(WorldChunk chunk) {
        int holes = 0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        int minX = chunk.getPos().getStartX();
        int minZ = chunk.getPos().getStartZ();
        int bottom = chunk.getBottomY() + 1;
        for (int x = 1; x < 15; x++) {
            columns:
            for (int z = 1; z < 15; z++) {
                int run = 0;
                for (int y = bottom; y <= 0; y++) {
                    if (isShaftCell(chunk, p, minX + x, y, minZ + z)) {
                        if (++run < 3) continue;
                        holes++;
                        continue columns;
                    }
                    run = 0;
                }
            }
        }
        return holes;
    }

    private static boolean isShaftCell(WorldChunk c, BlockPos.Mutable p, int x, int y, int z) {
        if (!c.getBlockState(p.set(x, y, z)).isAir()) return false;
        return solid(c, p.set(x + 1, y, z)) && solid(c, p.set(x - 1, y, z))
                && solid(c, p.set(x, y, z + 1)) && solid(c, p.set(x, y, z - 1));
    }

    private static boolean solid(WorldChunk c, BlockPos p) {
        BlockState s = c.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void onRender3D(WorldRenderContext context) {
        Set<ChunkPos> set = painted;
        if (set.isEmpty()) return;
        long now = System.currentTimeMillis();
        int[] base = rainbow.on() ? hsv((now % 4000L) / 4000.0f) : new int[]{255, 40, 40};
        int fill = (fillOpacity.i() << 24) | (base[0] << 16) | (base[1] << 8) | base[2];
        int line = (lineOpacity.i() << 24) | (base[0] << 16) | (base[1] << 8) | base[2];
        double y = RENDER_Y;

        Matrix4f m = Render3D.push(context);
        Render3D.begin();

        // filled squares first (one builder at a time)
        RenderSystem.disableCull();
        BufferBuilder qb = Render3D.quads();
        for (ChunkPos p : set) {
            double x = p.getStartX();
            double z = p.getStartZ();
            Render3D.horizontalQuad(qb, m, x, y, z, x + 16.0, z + 16.0, fill);
        }
        Render3D.flush(qb);
        RenderSystem.enableCull();

        BufferBuilder lb = Render3D.lines();
        for (ChunkPos p : set) {
            double x = p.getStartX();
            double z = p.getStartZ();
            Render3D.line(lb, m, new Vec3d(x, y, z), new Vec3d(x + 16.0, y, z), line);
            Render3D.line(lb, m, new Vec3d(x + 16.0, y, z), new Vec3d(x + 16.0, y, z + 16.0), line);
            Render3D.line(lb, m, new Vec3d(x + 16.0, y, z + 16.0), new Vec3d(x, y, z + 16.0), line);
            Render3D.line(lb, m, new Vec3d(x, y, z + 16.0), new Vec3d(x, y, z), line);
        }
        int segs = ringSegments.i();
        int n = 0;
        Vec3d cam = context.camera().getPos();
        Vec3d start = cam.add(mc.player.getRotationVec(1.0f)); // tracers start just in front of the camera
        for (double[] r : regions) {
            double radius = r[2] + (animation.on() ? 1.5 + 1.5 * Math.sin(now / 300.0) : 0.0);
            for (int i = 0; i < segs; i++) {
                double a1 = Math.PI * 2 * i / segs;
                double a2 = Math.PI * 2 * (i + 1) / segs;
                Render3D.line(lb, m,
                        new Vec3d(r[0] + Math.cos(a1) * radius, y, r[1] + Math.sin(a1) * radius),
                        new Vec3d(r[0] + Math.cos(a2) * radius, y, r[1] + Math.sin(a2) * radius), line);
            }
            if (tracers.on() && n++ < MAX_TRACERS) {
                Render3D.line(lb, m, start, new Vec3d(r[0], y, r[1]), line);
            }
        }
        Render3D.flush(lb);
        Render3D.end();
        Render3D.pop(context);
    }

    private static int[] hsv(float h) {
        float f = h * 6.0f;
        int i = (int) f % 6;
        float q = f - (int) f;
        float qq = 1.0f - q;
        float r, g, b;
        switch (i) {
            case 0 -> { r = 1.0f; g = q; b = 0.0f; }
            case 1 -> { r = qq; g = 1.0f; b = 0.0f; }
            case 2 -> { r = 0.0f; g = 1.0f; b = q; }
            case 3 -> { r = 0.0f; g = qq; b = 1.0f; }
            case 4 -> { r = q; g = 0.0f; b = 1.0f; }
            default -> { r = 1.0f; g = 0.0f; b = qq; }
        }
        return new int[]{(int) (r * 255.0f), (int) (g * 255.0f), (int) (b * 255.0f)};
    }

    private record Result(int heat, boolean hasUngrown, List<BlockPos> rotated, int holes) {
    }
}
