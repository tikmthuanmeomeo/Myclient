/*
 * Ported from ArrowAddon (MIT License, (c) Nhân-Tài (ArrowAddon)), see THIRD_PARTY_NOTICES/ArrowAddon-LICENSE.txt.
 */
package abcxyz.module.render;

import abcxyz.compat.Render3DEvent;
import abcxyz.compat.SettingColor;
import abcxyz.compat.ShapeMode;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.KeySetting;
import abcxyz.core.Module;
import abcxyz.core.Notify;
import abcxyz.core.NumSetting;
import abcxyz.render.SusZoneFx;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.util.InputUtil;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.util.Formatting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.item.ItemConvertible;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.TextColor;
import net.minecraft.client.world.ClientWorld;

public class SigalChunkFinder extends Module {
    private final NumSetting simulationDistance;
    private final NumSetting sensitivity;
    private final BoolSetting autoSensitivity;
    private final NumSetting autoRange;
    private final KeySetting restoreSensitivity;
    private final BoolSetting kelp;
    private final BoolSetting caveVine;
    private final BoolSetting vines;
    private final BoolSetting amethyst;
    private final BoolSetting bamboo;
    private final BoolSetting cocoa;
    private final BoolSetting beeNest;
    private final BoolSetting rotatedDeepslate;
    private final NumSetting caveVineMinAge;
    private final BoolSetting caveVineYoungCancel;
    private final NumSetting caveVineWeight;
    private final NumSetting vineMinLength;
    private final BoolSetting vineOncePerColumn;
    private final NumSetting vineWeight;
    private final NumSetting vineMinY;
    private final NumSetting vineMaxY;
    private final SettingColor color;
    private final NumSetting fillOpacity;
    private final NumSetting lineOpacity;
    private final NumSetting boxWidth;
    private final NumSetting boxLength;
    private final NumSetting hideDistance;
    private final BoolSetting centerPillar;
    private final NumSetting pillarTop;
    private final BoolSetting tracers;
    private final Map<Long, Scan> scans;
    private volatile long scanVersion;
    private List<Island> islandCache;
    private long cacheVer;
    private int cacheSens;
    private int cacheR;
    private final Map<Long, Integer> spread;
    private final Map<Long, Integer> pending;
    private ClientWorld lastWorld;
    private int savedSensitivity;
    private boolean restoreWasDown = false;
    private boolean saidDigDown;
    private final Set<Long> announced;
    private final Map<Long, Integer> stableTicks;
    private static final int STABLE_TICKS_NEEDED = 20;
    private final Queue<int[]> pendingToasts;
    private int[] currentToast;
    private long toastStart;
    private ItemStack toastIcon;
    private int toastTick;
    private static final int TOAST_H = 34;
    private static final int TOAST_BORDER = 2;
    private static final long TOAST_SLIDE_MS = 350L;
    private static final long TOAST_HOLD_MS = 4000L;
    private static final long RGB_CYCLE_MS = 9000L;
    private final SusZoneFx fx;
    private final SusZoneFx.Style style;
    private final ExecutorService executor;
    private final List<int[]> ring;
    private int ringCursor;
    private final Set<Long> queued;
    private static final int NEAR_COUNT = 121;
    private int nearCursor;
    private int fillTick;
    private final Map<Long, Integer> dueScans;
    private static final double RING_Y = 60.0;

    private NumSetting intS(String name, String desc, int def, int min, int max) {
        NumSetting s = add(new NumSetting(pascal(name), def, min, max, 1));
        s.label = pretty(name);
        return s;
    }

    private BoolSetting boolS(String name, String desc, boolean def) {
        BoolSetting s = add(new BoolSetting(pascal(name), def));
        s.label = pretty(name);
        return s;
    }

    public SigalChunkFinder() {
        super("SigalChunkFinder", "Finds probable base locations using growth and rotated-deepslate signals.", Category.BaseFinder);
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> this.onChunk(chunk));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.resetState());
        this.simulationDistance = this.intS("scan-radius", "Chunks around each scanned chunk that receive its signals.", 12, 1, 20);
        this.sensitivity = this.intS("threshold", "Signals a chunk needs before it gets marked.", 5, 2, 16);
        this.autoSensitivity = this.boolS("sensitivity [2]", "Near a marked area, set the threshold to that chunk's own score.", false);
        this.autoRange = this.intS("range-distance", "Distance in blocks to a marked chunk at which the automatic threshold applies.", 10, 0, 48);
        this.restoreSensitivity = add(new KeySetting("RestoreKeybind", -1));
        this.restoreSensitivity.label = "Restore keybind";
        this.kelp = this.boolS("kelp-growth", "Counts grown kelp.", true);
        this.caveVine = this.boolS("cave-vine-growth", "Counts grown cave vines.", false);
        this.vines = this.boolS("vine-growth", "Counts grown vines.", true);
        this.amethyst = this.boolS("amethyst-growth", "Counts amethyst clusters and buds.", true);
        this.bamboo = this.boolS("bamboo-growth", "Counts grown bamboo.", false);
        this.cocoa = this.boolS("cocoa-pods", "Counts ripe cocoa pods.", false);
        this.beeNest = this.boolS("bee-honey", "Counts full bee nests (a full nest is shown immediately).", true);
        this.rotatedDeepslate = this.boolS("deepslate-axis", "Counts enclosed sideways deepslate.", true);
        this.caveVineMinAge = this.intS("cave-vine-min-age", "Age (0-25) a cave vine needs to count as grown.", 25, 0, 25);
        this.caveVineYoungCancel = this.boolS("cave-vine-young-cancel", "Ignore cave vines in a chunk that has more young ones than grown ones.", true);
        this.caveVineWeight = this.intS("cave-vine-weight", "Points each grown cave vine adds.", 1, 1, 10);
        this.vineMinLength = this.intS("vine-min-length", "Vine blocks in a row (counting upward) for a vine to count as grown.", 23, 2, 35);
        this.vineOncePerColumn = this.boolS("vine-once-per-column", "Count each vine column once (its lowest block).", false);
        this.vineWeight = this.intS("vine-weight", "Points each grown vine adds.", 1, 1, 10);
        this.vineMinY = this.intS("vine-min-y", "Lowest Y a vine may count at.", -64, -64, 320);
        this.vineMaxY = this.intS("vine-max-y", "Highest Y a vine may count at.", 320, -64, 320);
        this.color = new SettingColor(255, 255, 255, 255);
        this.fillOpacity = this.intS("rect-fill-alpha", "Opacity of the filled rectangle.", 65, 0, 255);
        this.lineOpacity = this.intS("outline-alpha", "Opacity of the rectangle outline.", 230, 0, 255);
        this.boxWidth = this.intS("rect-size-x", "Rectangle size on the X axis (blocks).", 54, 2, 128);
        this.boxLength = this.intS("rect-size-z", "Rectangle size on the Z axis (blocks).", 64, 2, 128);
        this.hideDistance = this.intS("hide-distance", "Hide the rectangle, pillar and tracer when farther than this many blocks.", 850, 64, 4096);
        this.centerPillar = this.boolS("center-pillar", "Glowing pillar at the chunk center, from Pillar Top Y down to bedrock.", true);
        this.pillarTop = this.intS("center-pillar-top-y", "Top Y of the center pillar.", 160, -64, 320);
        this.tracers = this.boolS("tracer-line", "Draw a line from the crosshair to the centre of each marked area.", true);
        this.scans = new ConcurrentHashMap<Long, Scan>();
        this.islandCache = new ArrayList<Island>();
        this.cacheVer = -1L;
        this.cacheSens = -1;
        this.cacheR = -1;
        this.spread = new ConcurrentHashMap<Long, Integer>();
        this.pending = new ConcurrentHashMap<Long, Integer>();
        this.savedSensitivity = -1;
        this.saidDigDown = false;
        this.announced = ConcurrentHashMap.newKeySet();
        this.stableTicks = new HashMap<Long, Integer>();
        this.pendingToasts = new ConcurrentLinkedQueue<int[]>();
        this.fx = new SusZoneFx();
        this.style = new SusZoneFx.Style();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "sigal-chunk-scan");
            t.setDaemon(true);
            return t;
        });
        this.ring = SigalChunkFinder.buildRing();
        this.ringCursor = 0;
        this.queued = ConcurrentHashMap.newKeySet();
        this.nearCursor = 0;
        this.fillTick = 0;
        this.dueScans = new ConcurrentHashMap<Long, Integer>();
        this.autoSensitivity.with(this.autoRange);
        this.caveVine.with(this.caveVineMinAge, this.caveVineYoungCancel, this.caveVineWeight);
        this.vines.with(this.vineMinLength, this.vineOncePerColumn, this.vineWeight, this.vineMinY, this.vineMaxY);
        this.centerPillar.with(this.pillarTop);
    }

    private void restoreSavedSensitivity() {
        if (this.savedSensitivity != -1) {
            this.sensitivity.set((double) (this.savedSensitivity));
            this.savedSensitivity = -1;
        }
    }

    @Override
    public void onEnable() {
        this.clearAll();
        this.lastWorld = this.mc.world;
    }

    @Override
    public void onDisable() {
        this.restoreSavedSensitivity();
        this.clearAll();
    }

    public void resetState() {
        this.clearAll();
    }

    private void clearAll() {
        if (this.fx != null) {
            this.fx.clear();
        }
        this.queued.clear();
        this.ringCursor = 0;
        this.scans.clear();
        this.spread.clear();
        this.pending.clear();
        this.announced.clear();
        this.stableTicks.clear();
        this.dueScans.clear();
        this.nearCursor = 0;
        ++this.scanVersion;
        this.pendingToasts.clear();
        this.currentToast = null;
        this.saidDigDown = false;
        this.savedSensitivity = -1;
    }

    private static List<int[]> buildRing() {
        ArrayList<int[]> l = new ArrayList<int[]>();
        for (int dx = -20; dx <= 20; ++dx) {
            int dz = -20;
            while (dz <= 20) {
                l.add(new int[]{dx, dz++});
            }
        }
        l.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return l;
    }

    private void onChunk(WorldChunk chunk) {
        if (this.isEnabled() && chunk != null && this.mc.world != null) {
            this.queueScan(chunk.getPos().x, chunk.getPos().z);
        }
    }

    @Override
    public void onTick() {
        if (this.restoreSensitivity.key() >= 0 && this.mc.currentScreen == null) {
            boolean down = InputUtil.isKeyPressed(this.mc.getWindow().getHandle(), this.restoreSensitivity.key());
            if (down && !this.restoreWasDown) {
                this.restoreSavedSensitivity();
            }
            this.restoreWasDown = down;
        }
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }
        if (this.lastWorld != this.mc.world) {
            this.clearAll();
            this.lastWorld = this.mc.world;
        }
        this.scanTick();
        this.postTick();
    }

    private void scanTick() {
        int[] o;
        int i;
        ChunkPos pc = this.mc.player.getChunkPos();
        for (i = 0; i < 12; ++i) {
            o = this.ring.get(this.ringCursor++ % this.ring.size());
            this.queueScan(pc.x + o[0], pc.z + o[1]);
        }
        for (i = 0; i < 8; ++i) {
            o = this.ring.get(this.nearCursor++ % 121);
            this.queueScan(pc.x + o[0], pc.z + o[1]);
        }
        if (!this.dueScans.isEmpty()) {
            for (Map.Entry<Long, Integer> e : this.dueScans.entrySet()) {
                if (e.getValue() <= 1) {
                    this.dueScans.remove(e.getKey());
                    this.queueScan(ChunkPos.getPackedX((long)e.getKey()), ChunkPos.getPackedZ((long)e.getKey()));
                    continue;
                }
                this.dueScans.put(e.getKey(), e.getValue() - 1);
            }
        }
        if (++this.fillTick % 5 == 0) {
            this.fillCandidates();
        }
    }

    private void fillCandidates() {
        int sens = this.sensitivity.i();
        int r = this.simulationDistance.i();
        int budget = 40;
        for (Map.Entry<Long, Integer> e : this.spread.entrySet()) {
            if (e.getValue() < sens || this.pending.getOrDefault(e.getKey(), 0) > 0) continue;
            int cx = ChunkPos.getPackedX((long)e.getKey());
            int cz = ChunkPos.getPackedZ((long)e.getKey());
            for (int dx = -r; dx <= r && budget > 0; ++dx) {
                for (int dz = -r; dz <= r && budget > 0; ++dz) {
                    if (this.scans.containsKey(ChunkPos.toLong((int)(cx + dx), (int)(cz + dz))) || !this.mc.world.getChunkManager().isChunkLoaded(cx + dx, cz + dz)) continue;
                    this.queueScan(cx + dx, cz + dz);
                    --budget;
                }
            }
            if (budget > 0) continue;
            return;
        }
    }

    @Override
    public void onBlockUpdate(BlockPos pos, BlockState newState) {
        if (newState != null && this.interesting(newState)) {
            this.markDue(pos);
        }
    }

    private void markDue(BlockPos pos) {
        this.dueScans.putIfAbsent(ChunkPos.toLong((int)(pos.getX() >> 4), (int)(pos.getZ() >> 4)), 3);
    }

    private void queueScan(int cx, int cz) {
        long key = ChunkPos.toLong((int)cx, (int)cz);
        if (!this.queued.add(key)) {
            return;
        }
        this.executor.execute(() -> {
            try {
                if (!this.isEnabled() || this.mc.world == null) {
                    return;
                }
                ChunkPos cp = new ChunkPos(cx, cz);
                if (!this.mc.world.getChunkManager().isChunkLoaded(cx, cz)) {
                    Scan old = this.scans.remove(key);
                    if (old != null) {
                        this.apply(cp, old, -1);
                        ++this.scanVersion;
                    }
                    return;
                }
                WorldChunk wc = this.mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (wc == null) {
                    return;
                }
                Scan fresh = this.scan(wc);
                Scan old = this.scans.get(key);
                if (old != null && SigalChunkFinder.sameScan(old, fresh)) {
                    return;
                }
                this.scans.put(key, fresh);
                if (old != null) {
                    this.apply(cp, old, -1);
                }
                this.apply(cp, fresh, 1);
                ++this.scanVersion;
            }
            catch (Throwable throwable) {
            }
            finally {
                this.queued.remove(key);
            }
        });
    }

    private static boolean sameScan(Scan a, Scan b) {
        return a.done() == b.done() && a.pending() == b.pending() && a.radius() == b.radius() && a.sites().equals(b.sites()) && a.nests().equals(b.nests()) && Arrays.equals(a.bb(), b.bb());
    }

    private void apply(ChunkPos cp, Scan sc, int sign) {
        int r = sc.radius();
        for (int dx = -r; dx <= r; ++dx) {
            for (int dz = -r; dz <= r; ++dz) {
                long k = ChunkPos.toLong((int)(cp.x + dx), (int)(cp.z + dz));
                if (sc.done() > 0 && this.spread.merge(k, sign * sc.done(), Integer::sum) == 0) {
                    this.spread.remove(k);
                }
                if (sc.pending() <= 0 || this.pending.merge(k, sign, Integer::sum) != 0) continue;
                this.pending.remove(k);
            }
        }
    }

    private Scan scan(WorldChunk chunk) {
        int[] c = new int[2];
        HashSet<BlockPos> sites = new HashSet<BlockPos>();
        ArrayList<BlockPos> nests = new ArrayList<BlockPos>();
        boolean cluster = false;
        boolean bud = false;
        int kelpGrown = 0;
        int kelpYoung = 0;
        int[] kelpBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        int[] bb = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        BlockPos.Mutable p = new BlockPos.Mutable();
        int caveAgeMin = this.caveVineMinAge.i();
        int vLen = this.vineMinLength.i();
        int vLo = Math.min(this.vineMinY.i(), this.vineMaxY.i());
        int vHi = Math.max(this.vineMinY.i(), this.vineMaxY.i());
        boolean vOnce = this.vineOncePerColumn.on();
        int caveGrown = 0;
        int caveYoung = 0;
        int vineGrown = 0;
        int[] caveBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        int[] vineBB = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        BlockPos.Mutable q = new BlockPos.Mutable();
        for (int si = 0; si < sections.length; ++si) {
            ChunkSection sec = sections[si];
            if (sec == null || sec.isEmpty() || !sec.hasAny(this::interesting)) continue;
            int by = bottom + si << 4;
            for (int x = 0; x < 16; ++x) {
                for (int y = 0; y < 16; ++y) {
                    for (int z = 0; z < 16; ++z) {
                        BlockState s = sec.getBlockState(x, y, z);
                        if (!this.interesting(s)) continue;
                        p.set((chunk.getPos().x << 4) + x, by + y, (chunk.getPos().z << 4) + z);
                        Block b = s.getBlock();
                        if (b == Blocks.KELP) {
                            if (s.contains((Property)Properties.AGE_25) && (Integer)s.get((Property)Properties.AGE_25) >= 25) {
                                ++kelpGrown;
                                SigalChunkFinder.mark(kelpBB, (BlockPos)p);
                                continue;
                            }
                            ++kelpYoung;
                            continue;
                        }
                        if (b == Blocks.CAVE_VINES) {
                            if (s.contains((Property)Properties.AGE_25) && (Integer)s.get((Property)Properties.AGE_25) >= caveAgeMin) {
                                ++caveGrown;
                                SigalChunkFinder.mark(caveBB, (BlockPos)p);
                                continue;
                            }
                            ++caveYoung;
                            continue;
                        }
                        if (b == Blocks.VINE) {
                            if (p.getY() < vLo || p.getY() > vHi || !SigalChunkFinder.vineColumnOk(chunk, q, p.getX(), p.getY(), p.getZ(), vLen, vOnce)) continue;
                            ++vineGrown;
                            SigalChunkFinder.mark(vineBB, (BlockPos)p);
                            continue;
                        }
                        if (b == Blocks.AMETHYST_CLUSTER) {
                            cluster = true;
                            SigalChunkFinder.mark(bb, (BlockPos)p);
                            continue;
                        }
                        if (b == Blocks.SMALL_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.LARGE_AMETHYST_BUD) {
                            bud = true;
                            continue;
                        }
                        this.weigh(s, (BlockPos)p, c, bb, sites);
                        if (b != Blocks.BEE_NEST || (Integer)s.get((Property)Properties.HONEY_LEVEL) != 5) continue;
                        nests.add(p.toImmutable());
                    }
                }
            }
        }
        if (kelpYoung > kelpGrown) {
            c[1] = c[1] + 1;
        } else if (kelpGrown > 0) {
            c[0] = c[0] + kelpGrown;
            bb[0] = Math.min(bb[0], kelpBB[0]);
            bb[1] = Math.min(bb[1], kelpBB[1]);
            bb[2] = Math.max(bb[2], kelpBB[2]);
            bb[3] = Math.max(bb[3], kelpBB[3]);
        }
        if (this.caveVineYoungCancel.on() && caveYoung > caveGrown) {
            c[1] = c[1] + 1;
        } else if (caveGrown > 0) {
            c[0] = c[0] + caveGrown * this.caveVineWeight.i();
            SigalChunkFinder.mergeBB(bb, caveBB);
        }
        if (vineGrown > 0) {
            c[0] = c[0] + vineGrown * this.vineWeight.i();
            SigalChunkFinder.mergeBB(bb, vineBB);
        }
        if (cluster) {
            c[0] = c[0] + 1;
        }
        if (bud) {
            c[1] = c[1] + 1;
        }
        return new Scan(c[0], c[1], sites, this.simulationDistance.i(), bb[0] == Integer.MAX_VALUE ? null : bb, nests);
    }

    private boolean interesting(BlockState s) {
        Block b = s.getBlock();
        return this.kelp.on() != false && b == Blocks.KELP || this.caveVine.on() != false && b == Blocks.CAVE_VINES || this.vines.on() != false && b == Blocks.VINE || this.bamboo.on() != false && b == Blocks.BAMBOO || this.cocoa.on() != false && b == Blocks.COCOA || this.beeNest.on() != false && b == Blocks.BEE_NEST || this.amethyst.on() != false && (b == Blocks.AMETHYST_CLUSTER || b == Blocks.LARGE_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.SMALL_AMETHYST_BUD) || this.rotatedDeepslate.on() != false && b == Blocks.DEEPSLATE;
    }

    private static void mergeBB(int[] bb, int[] o) {
        if (o[0] == Integer.MAX_VALUE) {
            return;
        }
        bb[0] = Math.min(bb[0], o[0]);
        bb[1] = Math.min(bb[1], o[1]);
        bb[2] = Math.max(bb[2], o[2]);
        bb[3] = Math.max(bb[3], o[3]);
    }

    private static boolean vineColumnOk(WorldChunk chunk, BlockPos.Mutable q, int wx, int wy, int wz, int len, boolean once) {
        if (once && chunk.getBlockState((BlockPos)q.set(wx, wy - 1, wz)).getBlock() == Blocks.VINE) {
            return false;
        }
        for (int k = 1; k < len; ++k) {
            if (chunk.getBlockState((BlockPos)q.set(wx, wy + k, wz)).getBlock() == Blocks.VINE) continue;
            return false;
        }
        return true;
    }

    private static void mark(int[] bb, BlockPos p) {
        bb[0] = Math.min(bb[0], p.getX());
        bb[1] = Math.min(bb[1], p.getZ());
        bb[2] = Math.max(bb[2], p.getX());
        bb[3] = Math.max(bb[3], p.getZ());
    }

    private void weigh(BlockState s, BlockPos pos, int[] c, int[] bb, Set<BlockPos> sites) {
        Block b = s.getBlock();
        if (b == Blocks.BAMBOO) {
            if (this.mc.world.getBlockState(pos.up()).isOf(Blocks.BAMBOO)) {
                return;
            }
            boolean done = (Integer)s.get((Property)Properties.STAGE) == 1;
            int n = done ? 0 : 1;
            c[n] = c[n] + 1;
            if (done) {
                SigalChunkFinder.mark(bb, pos);
            }
        } else if (b == Blocks.COCOA) {
            boolean done = (Integer)s.get((Property)Properties.AGE_2) == 2;
            int n = done ? 0 : 1;
            c[n] = c[n] + 1;
            if (done) {
                SigalChunkFinder.mark(bb, pos);
            }
        } else if (b == Blocks.BEE_NEST) {
            boolean full = (Integer)s.get((Property)Properties.HONEY_LEVEL) == 5;
            int n = full ? 0 : 1;
            c[n] = c[n] + 1;
            if (full) {
                SigalChunkFinder.mark(bb, pos);
            }
        } else if (b == Blocks.DEEPSLATE) {
            if (s.get((Property)Properties.AXIS) == Direction.Axis.Y) {
                return;
            }
            if (pos.getY() < 0 || pos.getY() > 60) {
                return;
            }
            for (Direction d : Direction.values()) {
                if (!this.mc.world.getBlockState(pos.offset(d)).isAir()) continue;
                return;
            }
            c[0] = c[0] + 1;
            SigalChunkFinder.mark(bb, pos);
            sites.add(pos.toImmutable());
        }
    }

    private boolean isFlagged(long chunk, int score, int sens) {
        return score >= sens && this.pending.getOrDefault(chunk, 0) <= 0 && this.confirmed(chunk);
    }

    private boolean confirmed(long chunk) {
        int r = this.simulationDistance.i();
        int cx = ChunkPos.getPackedX((long)chunk);
        int cz = ChunkPos.getPackedZ((long)chunk);
        for (int dx = -r; dx <= r; ++dx) {
            for (int dz = -r; dz <= r; ++dz) {
                if (this.scans.containsKey(ChunkPos.toLong((int)(cx + dx), (int)(cz + dz)))) continue;
                return false;
            }
        }
        return true;
    }

    private List<Island> islands() {
        int sens = this.sensitivity.i();
        int r = this.simulationDistance.i();
        long v = this.scanVersion;
        if (v != this.cacheVer || sens != this.cacheSens || r != this.cacheR) {
            this.islandCache = this.buildIslandsNow();
            this.cacheVer = v;
            this.cacheSens = sens;
            this.cacheR = r;
        }
        return this.islandCache;
    }

    private List<Island> buildIslandsNow() {
        int sens = this.sensitivity.i();
        HashMap<Long, Integer> flagged = new HashMap<Long, Integer>();
        for (Map.Entry<Long, Integer> e : this.spread.entrySet()) {
            if (!this.isFlagged(e.getKey(), e.getValue(), sens)) continue;
            flagged.put(e.getKey(), e.getValue());
        }
        ArrayList<Island> out = new ArrayList<Island>();
        HashSet<Long> seen = new HashSet<Long>();
        Iterator iterator = flagged.keySet().iterator();
        while (iterator.hasNext()) {
            long start = (Long)iterator.next();
            if (!seen.add(start)) continue;
            Island is = new Island();
            ArrayDeque<Long> q = new ArrayDeque<Long>();
            q.add(start);
            double sw = 0.0;
            double sx = 0.0;
            double sz = 0.0;
            while (!q.isEmpty()) {
                int[][] n;
                long k = (Long)q.poll();
                int cx = ChunkPos.getPackedX((long)k);
                int cz = ChunkPos.getPackedZ((long)k);
                int c = (Integer)flagged.get(k);
                is.chunks.add(new long[]{cx, cz, c});
                double w = c - sens + 1;
                sw += w;
                sx += w * (double)cx;
                sz += w * (double)cz;
                is.maxContribution = Math.max(is.maxContribution, c);
                Scan sc = this.scans.get(k);
                if (sc != null) {
                    is.sites.addAll(sc.sites());
                }
                for (int[] d : n = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    long nk = ChunkPos.toLong((int)(cx + d[0]), (int)(cz + d[1]));
                    if (!flagged.containsKey(nk) || !seen.add(nk)) continue;
                    q.add(nk);
                }
            }
            is.mx = sx / sw;
            is.mz = sz / sw;
            double far = 0.0;
            for (long[] c : is.chunks) {
                far = Math.max(far, Math.hypot((double)c[0] - is.mx, (double)c[1] - is.mz));
            }
            is.maxMargin = is.maxContribution - sens;
            is.radius = 12.0 + 16.0 * far + 1.25 * (double)Math.min(is.maxMargin, 8);
            is.centreChunk = ChunkPos.toLong((int)((int)Math.floor((is.mx * 16.0 + 8.5) / 16.0)), (int)((int)Math.floor((is.mz * 16.0 + 8.5) / 16.0)));
            out.add(is);
        }
        this.assignRects(out, flagged);
        this.addNestSites(out);
        return out;
    }

    private void addNestSites(List<Island> out) {
        for (Map.Entry<Long, Scan> e : this.scans.entrySet()) {
            List<BlockPos> ns = e.getValue().nests();
            if (ns == null || ns.isEmpty()) continue;
            double x1 = Double.MAX_VALUE;
            double z1 = Double.MAX_VALUE;
            double x2 = -1.7976931348623157E308;
            double z2 = -1.7976931348623157E308;
            for (BlockPos np : ns) {
                x1 = Math.min(x1, (double)np.getX());
                z1 = Math.min(z1, (double)np.getZ());
                x2 = Math.max(x2, (double)(np.getX() + 1));
                z2 = Math.max(z2, (double)(np.getZ() + 1));
            }
            boolean covered = false;
            for (Island o : out) {
                if (!o.hasRect || !(x1 >= o.rx1) || !(z1 >= o.rz1) || !(x2 <= o.rx2) || !(z2 <= o.rz2)) continue;
                covered = true;
                break;
            }
            if (covered) continue;
            Island island = new Island();
            island.hasRect = true;
            island.instant = true;
            island.rx1 = x1;
            island.rz1 = z1;
            island.rx2 = x2;
            island.rz2 = z2;
            island.maxMargin = 4;
            island.centreChunk = e.getKey();
            out.add(island);
        }
    }

    private void assignRects(List<Island> islands, Map<Long, Integer> flagged) {
        if (islands.isEmpty()) {
            return;
        }
        HashMap<Long, Integer> owner = new HashMap<Long, Integer>();
        for (int i = 0; i < islands.size(); ++i) {
            for (long[] c : islands.get((int)i).chunks) {
                owner.put(ChunkPos.toLong((int)((int)c[0]), (int)((int)c[1])), i);
            }
        }
        for (Map.Entry<Long, Scan> e : this.scans.entrySet()) {
            Scan sc = e.getValue();
            if (sc.done() <= 0 || sc.bb() == null) continue;
            int cx = ChunkPos.getPackedX((long)e.getKey());
            int cz = ChunkPos.getPackedZ((long)e.getKey());
            int r = sc.radius();
            int[] votes = new int[islands.size()];
            boolean any = false;
            for (int dx = -r; dx <= r; ++dx) {
                for (int dz = -r; dz <= r; ++dz) {
                    Integer o = (Integer)owner.get(ChunkPos.toLong((int)(cx + dx), (int)(cz + dz)));
                    if (o == null) continue;
                    int n = o;
                    votes[n] = votes[n] + 1;
                    any = true;
                }
            }
            if (!any) continue;
            int best = 0;
            for (int i = 1; i < votes.length; ++i) {
                if (votes[i] <= votes[best]) continue;
                best = i;
            }
            Island is = islands.get(best);
            int[] bb = sc.bb();
            if (!is.hasRect) {
                is.rx1 = bb[0];
                is.rz1 = bb[1];
                is.rx2 = bb[2] + 1;
                is.rz2 = bb[3] + 1;
                is.hasRect = true;
                continue;
            }
            is.rx1 = Math.min(is.rx1, (double)bb[0]);
            is.rz1 = Math.min(is.rz1, (double)bb[1]);
            is.rx2 = Math.max(is.rx2, (double)(bb[2] + 1));
            is.rz2 = Math.max(is.rz2, (double)(bb[3] + 1));
        }
    }

    private void postTick() {
        this.announceNewSites();
        if (!this.autoSensitivity.on()) {
            return;
        }
        int sens = this.sensitivity.i();
        int range = this.autoRange.i();
        double px = this.mc.player.getX();
        double pz = this.mc.player.getZ();
        double best = Double.MAX_VALUE;
        int bestScore = -1;
        for (Map.Entry<Long, Integer> e : this.spread.entrySet()) {
            boolean inside;
            double dz;
            if (!this.isFlagged(e.getKey(), e.getValue(), sens)) continue;
            double x0 = (double)ChunkPos.getPackedX((long)e.getKey()) * 16.0;
            double z0 = (double)ChunkPos.getPackedZ((long)e.getKey()) * 16.0;
            double dx = Math.max(Math.max(x0 - px, px - (x0 + 16.0)), 0.0);
            double d = Math.sqrt(dx * dx + (dz = Math.max(Math.max(z0 - pz, pz - (z0 + 16.0)), 0.0)) * dz);
            boolean bl = inside = d == 0.0;
            if (!inside && !(d < (double)range) || !(d < best)) continue;
            best = d;
            bestScore = e.getValue();
        }
        if (bestScore < 0) {
            this.saidDigDown = false;
            return;
        }
        int target = Math.min(bestScore, 16);
        if (target != sens) {
            if (this.savedSensitivity == -1) {
                this.savedSensitivity = sens;
            }
            this.sensitivity.set((double) (target));
        }
        this.saidDigDown = true;
    }

    private void announceNewSites() {
        List<Island> now = this.islands();
        HashSet<Long> present = new HashSet<Long>();
        for (Island is : now) {
            if (!is.hasRect) continue;
            present.add(is.centreChunk);
            int seen = this.stableTicks.merge(is.centreChunk, 1, Integer::sum);
            if (!is.instant && seen < 20 || !this.announced.add(is.centreChunk)) continue;
            int cx = (int)Math.round((is.rx1 + is.rx2) / 2.0);
            int cz = (int)Math.round((is.rz1 + is.rz2) / 2.0);
            if (this.pendingToasts.size() < 5) {
                this.pendingToasts.add(new int[]{cx, cz});
            }
            this.sendWarning(cx, cz);
        }
        this.stableTicks.keySet().retainAll(present);
    }

    private void sendWarning(int x, int z) {
        if (this.mc.player == null) {
            return;
        }
        MutableText msg = Text.literal((String)"[ ").formatted(Formatting.GRAY).append((Text)Text.literal((String)"Sigal Chunk Finder").setStyle(Style.EMPTY.withColor(TextColor.fromRgb((int)9518562)))).append((Text)Text.literal((String)" ] ").formatted(Formatting.GRAY)).append((Text)Text.literal((String)"WARNING !").formatted(Formatting.RED)).append((Text)Text.literal((String)(" X: " + x + " Z: " + z)).formatted(Formatting.WHITE));
        this.mc.player.sendMessage((Text)msg, false);
    }

    @Override
    public void onHud(DrawContext ctx) {
        this.renderToast(ctx);
    }

    private void renderToast(DrawContext ctx) {
        long total;
        long t;
        if (this.mc.player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.currentToast == null) {
            this.currentToast = this.pendingToasts.poll();
            if (this.currentToast == null) {
                return;
            }
            this.toastStart = now;
        }
        if ((t = now - this.toastStart) >= (total = 4700L)) {
            this.currentToast = null;
            return;
        }
        String title = "Sigal Chunk Finder";
        String coords = "X: " + this.currentToast[0] + "  Z: " + this.currentToast[1];
        int h = 34;
        int textX = 38;
        int padRight = 16;
        int textW = Math.max(this.mc.textRenderer.getWidth(title), this.mc.textRenderer.getWidth(coords));
        int w = 38 + textW + 16;
        double slide = t < 350L ? 1.0 - (double)t / 350.0 : (t > total - 350L ? (double)(t - (total - 350L)) / 350.0 : 0.0);
        slide *= slide;
        int sw = this.mc.getWindow().getScaledWidth();
        int sh = this.mc.getWindow().getScaledHeight();
        int x = (int)Math.round((double)sw * 0.74 - (double)w / 2.0);
        int y = Math.max(14, (int)Math.round((double)sh * 0.06)) - (int)Math.round(slide * 64.0);
        double radius = 12.92;
        float hue = (float)(now % 9000L) / 9000.0f;
        SigalChunkFinder.fillRound(ctx, x, y, w, 34, radius, 0, hue, true);
        SigalChunkFinder.fillRound(ctx, x + 2, y + 2, w - 4, 30, radius - 2.0, 0, hue, false);
        if (this.toastIcon == null) {
            this.toastIcon = new ItemStack((ItemConvertible)Items.SPYGLASS);
        }
        float scale = 1.25f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate((float)(x + 12), (float)y + (34.0f - 16.0f * scale) / 2.0f, 0.0f);
        ctx.getMatrices().scale(scale, scale, 1.0f);
        ctx.drawItem(this.toastIcon, 0, 0);
        ctx.getMatrices().pop();
        ctx.drawText(this.mc.textRenderer, title, x + 38, y + 8, -1, false);
        ctx.drawText(this.mc.textRenderer, coords, x + 38, y + 19, -4210753, false);
    }

    private static void fillRound(DrawContext ctx, int x, int y, int w, int h, double r, int unused, float hue, boolean rgb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0.0, Math.min(r, (double)Math.min(w, h) / 2.0));
        for (int i = 0; i < h; ++i) {
            double dy = (double)i + 0.5;
            double edge = dy < r ? r - dy : (dy > (double)h - r ? dy - ((double)h - r) : 0.0);
            int inset = edge > 0.0 ? (int)Math.round(r - Math.sqrt(Math.max(0.0, r * r - edge * edge))) : 0;
            int color = -16777216;
            if (rgb) {
                color = 0xFF000000 | Color.HSBtoRGB((hue + (float)i * 0.015f) % 1.0f, 0.85f, 1.0f) & 0xFFFFFF;
            }
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    private void renderWorld(Render3DEvent event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }
        SettingColor c = this.color;
        this.style.r = c.r;
        this.style.g = c.g;
        this.style.b = c.b;
        this.style.width = this.boxWidth.i();
        this.style.length = this.boxLength.i();
        this.style.fillAlpha = this.fillOpacity.i();
        this.style.lineAlpha = this.lineOpacity.i();
        this.style.rectY = 60.0;
        this.style.top = this.pillarTop.i();
        this.style.pillar = this.centerPillar.on();
        this.style.tracer = this.tracers.on();
        this.style.hideDistance = this.hideDistance.i();
        List<Island> list = this.islands();
        this.fx.begin();
        for (Island is : list) {
            if (!is.hasRect) continue;
            this.fx.touch((is.rx1 + is.rx2) / 2.0, (is.rz1 + is.rz2) / 2.0, 60.0, 48.0);
        }
        this.fx.render(event, this.style);
        abcxyz.compat.Color mark = new abcxyz.compat.Color(255, 255, 255, this.lineOpacity.i());
        for (Island is : list) {
            for (BlockPos s : is.sites) {
                double bx = s.getX();
                double by = s.getY();
                double bz = s.getZ();
                event.renderer.line(bx + 0.16, by + 1.02, bz + 0.16, bx + 0.84, by + 1.02, bz + 0.84, mark);
                event.renderer.line(bx + 0.16, by + 1.02, bz + 0.84, bx + 0.84, by + 1.02, bz + 0.16, mark);
                event.renderer.line(bx + 0.5, by + 0.08, bz + 0.5, bx + 0.5, by + 0.92, bz + 0.5, mark);
                event.renderer.line(bx + 0.5, by + 0.5, bz + 0.16, bx + 0.5, by + 0.5, bz + 0.84, mark);
            }
        }
    }

    private record Scan(int done, int pending, Set<BlockPos> sites, int radius, int[] bb, List<BlockPos> nests) {
    }

    private static final class Island {
        final List<long[]> chunks = new ArrayList<long[]>();
        double mx;
        double mz;
        double radius;
        int maxMargin;
        int maxContribution;
        long centreChunk;
        double rx1;
        double rz1;
        double rx2;
        double rz2;
        boolean hasRect;
        final Set<BlockPos> sites = new HashSet<BlockPos>();
        boolean instant;

        private Island() {
        }
    }

    private static String pascal(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String pretty(String name) {
        String s = name.replace('-', ' ').trim();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        Render3DEvent event = new Render3DEvent(context);
        renderWorld(event);
        event.flush();
    }
}
