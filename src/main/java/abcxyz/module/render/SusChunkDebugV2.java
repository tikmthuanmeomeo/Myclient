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
import abcxyz.compat.Color;

import java.lang.invoke.CallSite;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import abcxyz.compat.Color;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.Heightmap;
import net.minecraft.entity.passive.TraderLlamaEntity;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.enums.Thickness;
import net.minecraft.block.entity.SculkSensorBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.block.entity.SculkCatalystBlockEntity;
import net.minecraft.block.entity.SculkShriekerBlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.entity.VaultBlockEntity;

public class SusChunkDebugV2 extends Module {
    public final NumSetting sensitivity;
    public final NumSetting minSignals;
    public final NumSetting minDepth;
    public final NumSetting minY;
    public final NumSetting maxY;
    public final NumSetting mergeRadius;
    public final BoolSetting blockEntities;
    public final BoolSetting blocks;
    public final BoolSetting entities;
    public final BoolSetting structureFilter;
    public final NumSetting structureMargin;
    public final BoolSetting amethyst;
    public final NumSetting amethystScanRadius;
    public final NumSetting amethystThreshold;
    public final BoolSetting undergroundBase;
    public final NumSetting baseMinBlocks;
    public final BoolSetting nearbyChat;
    public final NumSetting nearbyRadius;
    public final BoolSetting beeNest;
    public final BoolSetting beeUndergroundOnly;
    public final NumSetting beehiveMinY;
    public final NumSetting beehiveMaxY;
    public final BoolSetting includeBeehives;
    public final BoolSetting includeBeeNests;
    public final BoolSetting beeLevel0;
    public final BoolSetting beeLevel1;
    public final BoolSetting beeLevel2;
    public final BoolSetting beeLevel3;
    public final BoolSetting beeLevel4;
    public final BoolSetting beeLevel5;
    public final BoolSetting pillager;
    public final NumSetting pillagerMin;
    public final BoolSetting llama;
    public final NumSetting llamaMin;
    public final NumSetting llamaDistance;
    public final BoolSetting dripstone;
    public final NumSetting stalactiteMinLength;
    public final NumSetting stalagmiteMinLength;
    public final NumSetting dripstoneAlertLength;
    public final BoolSetting grownBamboo;
    public final NumSetting bambooMin;
    public final NumSetting bambooMinHeight;
    public final BoolSetting bambooUndergroundOnly;
    public final BoolSetting grownCaveVines;
    public final NumSetting caveVinesMin;
    public final BoolSetting grownKelp;
    public final NumSetting kelpMin;
    public final BoolSetting grownVines;
    public final NumSetting vineMin;
    public final NumSetting vineMinLength;
    public final NumSetting vineMinY;
    public final NumSetting vineMaxY;
    public final BoolSetting vineRequireGround;
    public final BoolSetting vineOncePerColumn;
    public final NumSetting caveVinesMinAge;
    public final BoolSetting caveVinesYoungCancel;
    public final BoolSetting rotatedDeepslate;
    public final NumSetting rotatedMin;
    public final BoolSetting rotatedEsp;
    public final BoolSetting rotatedTracers;
    public final SettingColor susColor;
    public final NumSetting fillAlpha;
    public final NumSetting lineAlpha;
    public final NumSetting boxWidth;
    public final NumSetting boxLength;
    public final NumSetting renderY;
    public final BoolSetting pillars;
    public final NumSetting pillarTop;
    public final NumSetting hideDistance;
    public final BoolSetting zoneTracer;
    public final BoolSetting chatCoords;
    private static final int K_BE = 1;
    private static final int K_FARM = 2;
    private static final int K_BUILD = 4;
    private static final int K_ENTITY = 8;
    private static final int K_CROP = 16;
    private static final int K_ROT = 32;
    private static final int K_AMETHYST = 64;
    private static final int K_BEE = 128;
    private static final int K_PILLAGER = 256;
    private static final int K_LLAMA = 512;
    private static final int K_DRIP = 1024;
    private static final int K_VINE = 2048;
    private static final int K_BAMBOO = 4096;
    private static final BInfo NONE = new BInfo(0.0f, 0, false, 0);
    private static final int M_STRONGHOLD = 1;
    private static final int M_DEEP = 2;
    private static final int M_MINE = 3;
    private static final int M_TRIAL = 4;
    private static final int M_DUNGEON = 5;
    private static final int M_SCULK = 6;
    private static final Map<Block, Boolean> TARGET_CACHE = new ConcurrentHashMap<Block, Boolean>();
    private static final Map<Block, BInfo> INFO_CACHE = new ConcurrentHashMap<Block, BInfo>();
    private static final int MAX_SIGS_PER_CHUNK = 500;
    private static final int FARM_SIGS_FOR_FARM = 4;
    private static final double ISOLATION_DIST = 10.0;
    private static final long GRACE_MS = 10000L;
    private static final double SWITCH_FACTOR = 2.0;
    private static final long SINGLE_KEY = 1L;
    private final Map<Long, ChunkSig> results;
    private final Map<Long, long[]> track;
    private final Set<Long> scanned;
    private final Map<Long, Integer> dueScans;
    private volatile boolean dirty;
    private volatile Cfg cfgNow;
    private volatile Map<Long, List<Sig>> lastEntBuckets;
    private final Map<Long, List<Target>> targetMap;
    private final Set<Long> notifiedTargets;
    private final Set<Long> naturalChunks;
    private final Set<Long> inFlight;
    private final Set<Long> announced;
    private volatile List<Zone> zones;
    private volatile boolean zoneContinuing;
    private final SusZoneFx fx;
    private final SusZoneFx.Style style;
    private Zone primary;
    private volatile List<int[]> rotBlocks;
    private static final int MAX_ROT_ESP = 64;
    private static final List<int[]> AM_RING = SusChunkDebugV2.buildAmRing();
    private final Map<Long, AmScan> amScans;
    private final Map<Long, Integer> amSpread;
    private final Map<Long, Integer> amPending;
    private final Set<Long> amQueued;
    private final Map<Long, Integer> amDue;
    private int amCursor;
    private ExecutorService executor;
    private ClientWorld lastWorld;
    private int tickCounter;

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

    private static List<int[]> buildAmRing() {
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

    private void clearAmethyst() {
        this.amScans.clear();
        this.amSpread.clear();
        this.amPending.clear();
        this.amQueued.clear();
        this.amDue.clear();
        this.amCursor = 0;
    }

    private static boolean isAmBud(BlockState s) {
        return s.isOf(Blocks.SMALL_AMETHYST_BUD) || s.isOf(Blocks.MEDIUM_AMETHYST_BUD) || s.isOf(Blocks.LARGE_AMETHYST_BUD);
    }

    private static boolean isAmRelevant(BlockState s) {
        return s.isOf(Blocks.AMETHYST_CLUSTER) || SusChunkDebugV2.isAmBud(s);
    }

    private void queueAmethyst(int cx, int cz) {
        ExecutorService ex = this.executor;
        if (ex == null || ex.isShutdown() || !SusChunkDebugV2.bool(this.amethyst)) {
            return;
        }
        long key = ChunkPos.toLong((int)cx, (int)cz);
        if (!this.amQueued.add(key)) {
            return;
        }
        try {
            ex.submit(() -> {
                try {
                    ClientWorld w = this.mc.world;
                    if (w == null) {
                        return;
                    }
                    WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
                    if (ch == null) {
                        AmScan old = this.amScans.remove(key);
                        if (old != null) {
                            this.amApply(cx, cz, old, -1);
                        }
                        return;
                    }
                    AmScan fresh = this.amScan(ch);
                    AmScan old = this.amScans.put(key, fresh);
                    if (old != null) {
                        this.amApply(cx, cz, old, -1);
                    }
                    this.amApply(cx, cz, fresh, 1);
                    this.dirty = true;
                }
                catch (Throwable throwable) {
                }
                finally {
                    this.amQueued.remove(key);
                }
            });
        }
        catch (Throwable t) {
            this.amQueued.remove(key);
        }
    }

    private AmScan amScan(WorldChunk chunk) {
        boolean cluster = false;
        boolean bud = false;
        for (ChunkSection sec : chunk.getSectionArray()) {
            if (sec == null || sec.isEmpty() || !sec.hasAny(SusChunkDebugV2::isAmRelevant)) continue;
            for (int x = 0; x < 16; ++x) {
                for (int y = 0; y < 16; ++y) {
                    for (int z = 0; z < 16; ++z) {
                        BlockState st = sec.getBlockState(x, y, z);
                        if (st.isOf(Blocks.AMETHYST_CLUSTER)) {
                            cluster = true;
                            continue;
                        }
                        if (!SusChunkDebugV2.isAmBud(st)) continue;
                        bud = true;
                    }
                }
            }
            if (cluster && bud) break;
        }
        return new AmScan(cluster ? 1 : 0, bud ? 1 : 0, SusChunkDebugV2.num(this.amethystScanRadius));
    }

    private void amApply(int cx, int cz, AmScan sc, int sign) {
        int r = sc.radius();
        for (int dx = -r; dx <= r; ++dx) {
            for (int dz = -r; dz <= r; ++dz) {
                long k = ChunkPos.toLong((int)(cx + dx), (int)(cz + dz));
                if (sc.done() > 0 && this.amSpread.merge(k, sign * sc.done(), Integer::sum) == 0) {
                    this.amSpread.remove(k);
                }
                if (sc.pending() <= 0 || this.amPending.merge(k, sign, Integer::sum) != 0) continue;
                this.amPending.remove(k);
            }
        }
    }

    private List<Long> amethystFlagged() {
        if (!SusChunkDebugV2.bool(this.amethyst) || this.amSpread.isEmpty()) {
            return List.of();
        }
        int need = SusChunkDebugV2.num(this.amethystThreshold);
        ArrayList<Long> out = new ArrayList<Long>();
        for (Map.Entry<Long, Integer> e : this.amSpread.entrySet()) {
            if (e.getValue() < need || this.amPending.getOrDefault(e.getKey(), 0) > 0) continue;
            out.add(e.getKey());
        }
        return out;
    }

    private void tickAmethyst() {
        if (!SusChunkDebugV2.bool(this.amethyst)) {
            if (!this.amScans.isEmpty()) {
                this.clearAmethyst();
            }
            return;
        }
        if (!this.amDue.isEmpty()) {
            for (Map.Entry<Long, Integer> e : this.amDue.entrySet()) {
                long key = e.getKey();
                if (e.getValue() <= 1) {
                    this.amDue.remove(key);
                    this.queueAmethyst(ChunkPos.getPackedX((long)key), ChunkPos.getPackedZ((long)key));
                    continue;
                }
                this.amDue.put(key, e.getValue() - 1);
            }
        }
        ChunkPos pc = this.mc.player.getChunkPos();
        for (int i = 0; i < 6; ++i) {
            int[] o = AM_RING.get(this.amCursor++ % AM_RING.size());
            this.queueAmethyst(pc.x + o[0], pc.z + o[1]);
        }
    }

    public SusChunkDebugV2() {
        super("SusChunkDebugV2", "Finds underground bases / base farms (through anti-xray) from block entities, blocks, bee nests, pillagers, llamas, dripstone, bamboo, kelp and vines. Few false alarms, only one sus chunk at a time.", Category.Render);
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> this.onChunk(chunk));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> this.resetState());
        this.sensitivity = this.intS("sensitivity", "Sensitivity", 5, 1, 40);
        this.minSignals = this.intS("min-signals", "Min Signals", 3, 2, 30);
        this.minDepth = this.intS("min-depth", "Min Depth", 6, 0, 64);
        this.minY = this.intS("min-y", "Min Y", -64, -64, 100);
        this.maxY = this.intS("max-y", "Max Y", 320, -64, 320);
        this.mergeRadius = this.intS("merge-radius", "Merge Radius", 6, 1, 6);
        this.blockEntities = this.boolS("block-entities", "Block Entities", true);
        this.blocks = this.boolS("block-signals", "Block Signals", true);
        this.entities = this.boolS("entity-signals", "Entity Signals", false);
        this.structureFilter = this.boolS("natural-structure-filter", "Natural Structure Filter", true);
        this.structureMargin = this.intS("structure-margin", "Structure Margin", 1, 0, 3);
        this.amethyst = this.boolS("amethyst", "Amethyst", true);
        this.amethystScanRadius = this.intS("amethyst-scan-radius", "Amethyst Scan Radius", 3, 1, 20);
        this.amethystThreshold = this.intS("amethyst-threshold", "Amethyst Threshold", 4, 1, 16);
        this.undergroundBase = this.boolS("underground-base", "Underground Base", true);
        this.baseMinBlocks = this.intS("base-min-blocks", "Base Min Blocks", 6, 1, 20);
        this.nearbyChat = this.boolS("nearby-blocks-chat", "Nearby Blocks Chat", true);
        this.nearbyRadius = this.intS("nearby-radius", "Nearby Radius", 1024, 16, 1024);
        this.beeNest = this.boolS("bee-nest", "Bee Nest", true);
        this.beeUndergroundOnly = this.boolS("bee-underground-only", "Bee Underground Only", false);
        this.beehiveMinY = this.intS("bee-min-y", "Bee Min Y", -64, -64, 128);
        this.beehiveMaxY = this.intS("bee-max-y", "Bee Max Y", 320, -64, 320);
        this.includeBeehives = this.boolS("bee-beehives", "Bee: Beehives", true);
        this.includeBeeNests = this.boolS("bee-bee-nests", "Bee: Bee Nests", true);
        this.beeLevel0 = this.boolS("bee-level-0", "Bee Level 0", false);
        this.beeLevel1 = this.boolS("bee-level-1", "Bee Level 1", false);
        this.beeLevel2 = this.boolS("bee-level-2", "Bee Level 2", false);
        this.beeLevel3 = this.boolS("bee-level-3", "Bee Level 3", false);
        this.beeLevel4 = this.boolS("bee-level-4", "Bee Level 4", false);
        this.beeLevel5 = this.boolS("bee-level-5", "Bee Level 5", true);
        this.pillager = this.boolS("pillager", "Pillager", true);
        this.pillagerMin = this.intS("pillager-min", "Pillager Min", 1, 1, 20);
        this.llama = this.boolS("llama", "Llama", true);
        this.llamaMin = this.intS("llama-min", "Llama Min", 1, 1, 20);
        this.llamaDistance = this.intS("llama-max-distance", "Llama Max Distance", 128, 16, 256);
        this.dripstone = this.boolS("dripstone", "Dripstone", false);
        this.stalactiteMinLength = this.intS("stalactite-min-length", "Stalactite Min Length", 16, 1, 16);
        this.stalagmiteMinLength = this.intS("stalagmite-min-length", "Stalagmite Min Length", 16, 1, 16);
        this.dripstoneAlertLength = this.intS("dripstone-alert-length", "Dripstone Alert Length", 20, 1, 20);
        this.grownBamboo = this.boolS("bamboo", "Bamboo", false);
        this.bambooMin = this.intS("bamboo-min-count", "Bamboo Min Count", 8, 1, 128);
        this.bambooMinHeight = this.intS("bamboo-min-height", "Bamboo Min Height", 12, 2, 16);
        this.bambooUndergroundOnly = this.boolS("bamboo-underground-only", "Bamboo Underground Only", false);
        this.grownCaveVines = this.boolS("cave-vines", "Cave Vines", true);
        this.caveVinesMin = this.intS("cave-vines-min-count", "Grown cave vine blocks a chunk needs to be flagged (Sus Chunk Finder: sensitivity 4).", 16, 1, 128);
        this.grownKelp = this.boolS("grown-kelp", "Grown Kelp", true);
        this.kelpMin = this.intS("kelp-min-count", "Kelp Min Count", 1, 1, 64);
        this.grownVines = this.boolS("grown-vines", "Grown Vines", true);
        this.vineMin = this.intS("vine-min-count", "Counted vine blocks a chunk needs to be flagged (Sus Chunk Finder: sensitivity 4).", 3, 1, 64);
        this.vineMinLength = this.intS("vine-min-length", "Vine blocks in a row (counting upward, itself included) for a vine block to count as grown. Sus Chunk Finder uses 5.", 23, 2, 35);
        this.vineMinY = this.intS("vine-min-y", "Vine Min Y", -64, -64, 128);
        this.vineMaxY = this.intS("vine-max-y", "Vine Max Y", 320, -64, 320);
        this.vineRequireGround = this.boolS("vine-require-ground", "Only count vine blocks whose block below is solid (old V2 rule). Off = Sus Chunk Finder logic.", false);
        this.vineOncePerColumn = this.boolS("vine-once-per-column", "Count each vine column once (its lowest block) instead of every block with enough vine above it.", false);
        this.caveVinesMinAge = this.intS("cave-vines-min-age", "Age (0-25) a cave vine block needs to count as grown. Sus Chunk Finder uses 25.", 25, 0, 25);
        this.caveVinesYoungCancel = this.boolS("cave-vines-young-cancel", "Ignore cave vines in a chunk that has more young ones than grown ones (Sus Chunk Finder 'ungrown' rule).", true);
        this.rotatedDeepslate = this.boolS("rotated-deepslate", "Rotated Deepslate", true);
        this.rotatedMin = this.intS("rotated-deepslate-min", "Rotated Deepslate Min", 1, 1, 32);
        this.rotatedEsp = this.boolS("rotated-esp", "Rotated ESP", true);
        this.rotatedTracers = this.boolS("rotated-tracers", "Rotated Tracers", true);
        this.susColor = new SettingColor(255, 40, 40, 255);
        this.fillAlpha = this.intS("rect-fill-alpha", "Opacity of the filled rectangle.", 65, 0, 255);
        this.lineAlpha = this.intS("outline-alpha", "Opacity of the rectangle outline.", 230, 0, 255);
        this.boxWidth = this.intS("rect-size-x", "Rectangle size on the X axis (blocks).", 54, 2, 128);
        this.boxLength = this.intS("rect-size-z", "Rectangle size on the Z axis (blocks).", 64, 2, 128);
        this.renderY = this.intS("render-y", "Render Y", 57, -64, 320);
        this.pillars = this.boolS("center-pillar", "Glowing pillar at the chunk center, from Pillar Top Y down to bedrock.", true);
        this.pillarTop = this.intS("center-pillar-top-y", "Top Y of the center pillar.", 160, -64, 320);
        this.hideDistance = this.intS("hide-distance", "Hide the rectangle, pillar and tracer when farther than this many blocks.", 850, 64, 4096);
        this.zoneTracer = this.boolS("zone-tracer", "Tracer line to the sus chunk.", false);
        this.chatCoords = this.boolS("chat-coords", "Chat Coords", true);
        this.results = new ConcurrentHashMap<Long, ChunkSig>();
        this.track = new ConcurrentHashMap<Long, long[]>();
        this.scanned = ConcurrentHashMap.newKeySet();
        this.dueScans = new ConcurrentHashMap<Long, Integer>();
        this.lastEntBuckets = Collections.emptyMap();
        this.targetMap = new ConcurrentHashMap<Long, List<Target>>();
        this.notifiedTargets = ConcurrentHashMap.newKeySet();
        this.naturalChunks = ConcurrentHashMap.newKeySet();
        this.inFlight = ConcurrentHashMap.newKeySet();
        this.announced = new HashSet<Long>();
        this.zones = Collections.emptyList();
        this.fx = new SusZoneFx();
        this.style = new SusZoneFx.Style();
        this.rotBlocks = Collections.emptyList();
        this.amScans = new ConcurrentHashMap<Long, AmScan>();
        this.amSpread = new ConcurrentHashMap<Long, Integer>();
        this.amPending = new ConcurrentHashMap<Long, Integer>();
        this.amQueued = ConcurrentHashMap.newKeySet();
        this.amDue = new ConcurrentHashMap<Long, Integer>();
        this.structureFilter.with(this.structureMargin);
        this.amethyst.with(this.amethystScanRadius, this.amethystThreshold);
        this.undergroundBase.with(this.baseMinBlocks);
        this.nearbyChat.with(this.nearbyRadius);
        this.beeNest.with(this.beeUndergroundOnly, this.beehiveMinY, this.beehiveMaxY, this.includeBeehives, this.includeBeeNests, this.beeLevel0, this.beeLevel1, this.beeLevel2, this.beeLevel3, this.beeLevel4, this.beeLevel5);
        this.pillager.with(this.pillagerMin);
        this.llama.with(this.llamaMin, this.llamaDistance);
        this.dripstone.with(this.stalactiteMinLength, this.stalagmiteMinLength, this.dripstoneAlertLength);
        this.grownBamboo.with(this.bambooMin, this.bambooMinHeight, this.bambooUndergroundOnly);
        this.grownCaveVines.with(this.caveVinesMin, this.caveVinesMinAge, this.caveVinesYoungCancel);
        this.grownKelp.with(this.kelpMin);
        this.grownVines.with(this.vineMin, this.vineMinLength, this.vineMinY, this.vineMaxY, this.vineRequireGround, this.vineOncePerColumn);
        this.rotatedDeepslate.with(this.rotatedMin, this.rotatedEsp, this.rotatedTracers);
        this.pillars.with(this.pillarTop);
    }

    private static boolean bool(BoolSetting s) {
        return s.on();
    }

    private static int num(NumSetting s) {
        return s.i();
    }

    @Override
    public void onEnable() {
        this.clearState();
        this.startExecutor();
    }

    private void startExecutor() {
        this.executor = Executors.newFixedThreadPool(Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 2)), r -> {
            Thread t = new Thread(r, "SusChunkDebugV2-scan");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onDisable() {
        if (this.executor != null && !this.executor.isShutdown()) {
            this.executor.shutdownNow();
        }
        this.executor = null;
        this.clearState();
    }

    public void resetState() {
        this.clearState();
    }

    private void clearState() {
        this.results.clear();
        this.track.clear();
        this.naturalChunks.clear();
        this.dueScans.clear();
        this.dirty = false;
        this.cfgNow = null;
        this.lastEntBuckets = Collections.emptyMap();
        this.targetMap.clear();
        this.notifiedTargets.clear();
        this.scanned.clear();
        this.inFlight.clear();
        this.clearAmethyst();
        this.announced.clear();
        this.primary = null;
        this.zones = Collections.emptyList();
        this.rotBlocks = Collections.emptyList();
        this.zoneContinuing = false;
        if (this.fx != null) {
            this.fx.clear();
        }
        this.lastWorld = null;
    }

    private void onChunk(WorldChunk ch) {
        if (!this.isEnabled() || ch == null) {
            return;
        }
        int cx = ch.getPos().x;
        int cz = ch.getPos().z;
        this.queueAmethyst(cx, cz);
        this.onChunkLoaded(cx, cz);
    }

    private void onChunkLoaded(int cx, int cz) {
        if (this.mc.world == null || this.mc.player == null || this.executor == null) {
            return;
        }
        if (!this.mc.world.getDimension().hasSkyLight()) {
            return;
        }
        WorldChunk chunk = this.mc.world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk == null) {
            return;
        }
        Cfg cfg = this.cfgNow != null ? this.cfgNow : this.snapshot();
        long key = chunk.getPos().toLong();
        this.submitScan(chunk, cfg, this.lastEntBuckets.getOrDefault(key, List.of()));
    }

    @Override
    public void onBlockUpdate(BlockPos pos, BlockState st) {
        if (pos == null || st == null) {
            return;
        }
        if (SusChunkDebugV2.isAmRelevant(st)) {
            this.amDue.putIfAbsent(ChunkPos.toLong((int)(pos.getX() >> 4), (int)(pos.getZ() >> 4)), 2);
        }
        if (SusChunkDebugV2.relevant(st)) {
            this.markDue(pos);
        }
    }

    private void markDue(BlockPos pos) {
        this.dueScans.putIfAbsent(ChunkPos.toLong((int)(pos.getX() >> 4), (int)(pos.getZ() >> 4)), 2);
    }

    private static boolean relevant(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.KELP || b == Blocks.VINE || b == Blocks.CAVE_VINES || b == Blocks.BAMBOO || b == Blocks.BEEHIVE || b == Blocks.BEE_NEST || b == Blocks.POINTED_DRIPSTONE) {
            return true;
        }
        if (b == Blocks.DEEPSLATE) {
            return s.contains((Property)Properties.AXIS) && s.get((Property)Properties.AXIS) != Direction.Axis.Y;
        }
        BInfo info = SusChunkDebugV2.infoOf(b);
        return info != NONE && info.marker() == 0;
    }

    @Override
    public void onTick() {
        boolean tenth;
        WorldChunk chunk;
        Cfg cfg;
        if (this.mc.world == null || this.mc.player == null) {
            return;
        }
        if (this.executor == null) {
            this.startExecutor();
        }
        if (this.lastWorld != this.mc.world) {
            this.clearState();
            this.lastWorld = this.mc.world;
        }
        if (!this.mc.world.getDimension().hasSkyLight()) {
            return;
        }
        this.tickAmethyst();
        long now = System.currentTimeMillis();
        ++this.tickCounter;
        this.cfgNow = cfg = this.snapshot();
        ChunkPos c = this.mc.player.getChunkPos();
        int r = Math.min(8, this.mc.options.getClampedViewDistance() + 1);
        if (!this.dueScans.isEmpty()) {
            for (Map.Entry<Long, Integer> e2 : this.dueScans.entrySet()) {
                long key = e2.getKey();
                if (e2.getValue() <= 1) {
                    this.dueScans.remove(key);
                    WorldChunk ch = this.mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX((long)key), ChunkPos.getPackedZ((long)key));
                    if (ch == null) continue;
                    this.submitScan(ch, cfg, this.lastEntBuckets.getOrDefault(key, List.of()));
                    continue;
                }
                this.dueScans.put(key, e2.getValue() - 1);
            }
        }
        if (this.tickCounter % 4 == 0) {
            int budget = 8;
            block1: for (int dx = -r; dx <= r; ++dx) {
                for (int dz = -r; dz <= r; ++dz) {
                    long key = ChunkPos.toLong((int)(c.x + dx), (int)(c.z + dz));
                    if (this.scanned.contains(key) || this.inFlight.contains(key) || (chunk = this.mc.world.getChunkManager().getWorldChunk(c.x + dx, c.z + dz)) == null) continue;
                    this.submitScan(chunk, cfg, this.lastEntBuckets.getOrDefault(key, List.of()));
                    if (--budget <= 0) break block1;
                }
            }
        }
        boolean bl = tenth = this.tickCounter % 10 == 0;
        if (tenth) {
            Map<Long, List<Sig>> entBuckets = SusChunkDebugV2.bool(this.entities) || SusChunkDebugV2.bool(this.pillager) || SusChunkDebugV2.bool(this.llama) ? this.collectEntities(cfg) : Collections.emptyMap();
            this.lastEntBuckets = entBuckets;
            boolean full = this.tickCounter % 60 == 0;
            for (long key : entBuckets.keySet()) {
                WorldChunk ch = this.mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX((long)key), ChunkPos.getPackedZ((long)key));
                if (ch == null) continue;
                this.submitScan(ch, cfg, entBuckets.getOrDefault(key, List.of()));
            }
            for (int dx = -r; dx <= r; ++dx) {
                for (int dz = -r; dz <= r; ++dz) {
                    boolean near;
                    chunk = this.mc.world.getChunkManager().getWorldChunk(c.x + dx, c.z + dz);
                    if (chunk == null) continue;
                    boolean bl2 = near = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                    if (!near && !full) continue;
                    this.submitScan(chunk, cfg, entBuckets.getOrDefault(chunk.getPos().toLong(), List.of()));
                }
            }
            this.track.entrySet().removeIf(e -> now - ((long[])e.getValue())[1] > 10000L);
            this.results.keySet().removeIf(k -> !this.track.containsKey(k) || this.mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX((long)k), ChunkPos.getPackedZ((long)k)) == null);
            this.naturalChunks.removeIf(k -> this.mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX((long)k), ChunkPos.getPackedZ((long)k)) == null);
            this.scanned.removeIf(k -> this.mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX((long)k), ChunkPos.getPackedZ((long)k)) == null);
            this.targetMap.keySet().removeIf(k -> this.mc.world.getChunkManager().getWorldChunk(ChunkPos.getPackedX((long)k), ChunkPos.getPackedZ((long)k)) == null);
        }
        if (tenth || this.dirty) {
            this.dirty = false;
            this.rebuildZones(now);
            if (SusChunkDebugV2.bool(this.nearbyChat)) {
                this.announceNearby();
            }
        }
    }

    private void announceNearby() {
        BlockPos pp = this.mc.player.getBlockPos();
        int rad = SusChunkDebugV2.num(this.nearbyRadius);
        double maxSq = (double)(rad + 32) * (double)(rad + 32);
        this.notifiedTargets.removeIf(k -> BlockPos.fromLong((long)k).getSquaredDistance((Vec3i)pp) > maxSq);
        long radSq = (long)rad * (long)rad;
        ArrayList<CallSite> found = new ArrayList<CallSite>();
        block0: for (List<Target> list : this.targetMap.values()) {
            for (Target t : list) {
                long dz;
                if (found.size() >= 12) continue block0;
                long dx = t.x() - pp.getX();
                if (dx * dx + (dz = (long)(t.z() - pp.getZ())) * dz > radSq || !this.notifiedTargets.add(BlockPos.asLong((int)t.x(), (int)t.y(), (int)t.z()))) continue;
                found.add((CallSite)((Object)(t.block().getName().getString() + " [" + t.x() + ", " + t.y() + ", " + t.z() + "]")));
            }
        }
        if (!found.isEmpty()) {
            Notify.chat("SusChunkDebugV2", "Machine blocks nearby: " + String.join((CharSequence)" | ", found));
        }
    }

    private Cfg snapshot() {
        int lo = Math.min(SusChunkDebugV2.num(this.minY), SusChunkDebugV2.num(this.maxY));
        int hi = Math.max(SusChunkDebugV2.num(this.minY), SusChunkDebugV2.num(this.maxY));
        return new Cfg(lo, hi, SusChunkDebugV2.num(this.minDepth), SusChunkDebugV2.num(this.minSignals), SusChunkDebugV2.num(this.sensitivity), SusChunkDebugV2.bool(this.blockEntities), SusChunkDebugV2.bool(this.blocks), SusChunkDebugV2.bool(this.grownKelp), SusChunkDebugV2.num(this.kelpMin), SusChunkDebugV2.bool(this.grownVines), SusChunkDebugV2.num(this.vineMin), SusChunkDebugV2.num(this.vineMinLength), SusChunkDebugV2.bool(this.structureFilter), SusChunkDebugV2.bool(this.rotatedDeepslate), SusChunkDebugV2.num(this.rotatedMin), SusChunkDebugV2.bool(this.undergroundBase), SusChunkDebugV2.num(this.baseMinBlocks), SusChunkDebugV2.bool(this.beeNest), SusChunkDebugV2.bool(this.beeUndergroundOnly), SusChunkDebugV2.bool(this.pillager), SusChunkDebugV2.num(this.pillagerMin), SusChunkDebugV2.bool(this.grownBamboo), SusChunkDebugV2.num(this.bambooMin), SusChunkDebugV2.bool(this.bambooUndergroundOnly), SusChunkDebugV2.bool(this.grownCaveVines), SusChunkDebugV2.num(this.caveVinesMin), SusChunkDebugV2.num(this.bambooMinHeight), Math.min(SusChunkDebugV2.num(this.vineMinY), SusChunkDebugV2.num(this.vineMaxY)), Math.max(SusChunkDebugV2.num(this.vineMinY), SusChunkDebugV2.num(this.vineMaxY)), Math.min(SusChunkDebugV2.num(this.beehiveMinY), SusChunkDebugV2.num(this.beehiveMaxY)), Math.max(SusChunkDebugV2.num(this.beehiveMinY), SusChunkDebugV2.num(this.beehiveMaxY)), SusChunkDebugV2.bool(this.includeBeehives), SusChunkDebugV2.bool(this.includeBeeNests), (SusChunkDebugV2.bool(this.beeLevel0) ? 1 : 0) | (SusChunkDebugV2.bool(this.beeLevel1) ? 2 : 0) | (SusChunkDebugV2.bool(this.beeLevel2) ? 4 : 0) | (SusChunkDebugV2.bool(this.beeLevel3) ? 8 : 0) | (SusChunkDebugV2.bool(this.beeLevel4) ? 16 : 0) | (SusChunkDebugV2.bool(this.beeLevel5) ? 32 : 0), SusChunkDebugV2.bool(this.llama), SusChunkDebugV2.num(this.llamaMin), SusChunkDebugV2.num(this.llamaDistance), SusChunkDebugV2.bool(this.dripstone), SusChunkDebugV2.num(this.stalactiteMinLength), SusChunkDebugV2.num(this.stalagmiteMinLength), SusChunkDebugV2.num(this.dripstoneAlertLength), SusChunkDebugV2.num(this.caveVinesMinAge), SusChunkDebugV2.bool(this.caveVinesYoungCancel), SusChunkDebugV2.bool(this.vineRequireGround), SusChunkDebugV2.bool(this.vineOncePerColumn));
    }

    private Map<Long, List<Sig>> collectEntities(Cfg cfg) {
        HashMap<Long, List<Sig>> out = new HashMap<Long, List<Sig>>();
        boolean regular = SusChunkDebugV2.bool(this.entities);
        for (Entity e : this.mc.world.getEntities()) {
            int top;
            BlockPos p;
            float w;
            if (cfg.llama() && e instanceof TraderLlamaEntity) {
                double d = cfg.llamaDist();
                if (Math.abs(e.getX() - this.mc.player.getX()) > d || Math.abs(e.getY() - this.mc.player.getY()) > d || Math.abs(e.getZ() - this.mc.player.getZ()) > d) continue;
                BlockPos lp = e.getBlockPos();
                out.computeIfAbsent(e.getChunkPos().toLong(), k -> new ArrayList()).add(new Sig(lp.getX(), lp.getY(), lp.getZ(), 1.0f, 512, false));
                continue;
            }
            if (cfg.pillager() && e instanceof PillagerEntity) {
                BlockPos pp = e.getBlockPos();
                out.computeIfAbsent(e.getChunkPos().toLong(), k -> new ArrayList()).add(new Sig(pp.getX(), pp.getY(), pp.getZ(), 1.0f, 256, false));
                continue;
            }
            if (!regular) continue;
            if (e instanceof ItemFrameEntity || e instanceof ArmorStandEntity) {
                w = 0.5f;
            } else if (e instanceof StorageMinecartEntity) {
                w = 0.8f;
            } else {
                if (!(e instanceof VillagerEntity)) continue;
                w = 0.3f;
            }
            if ((p = e.getBlockPos()).getY() < cfg.minY() || p.getY() > cfg.maxY()) continue;
            try {
                top = this.mc.world.getTopY(Heightmap.Type.WORLD_SURFACE, p.getX(), p.getZ());
            }
            catch (Throwable t) {
                continue;
            }
            if (p.getY() > top - cfg.depth()) continue;
            out.computeIfAbsent(e.getChunkPos().toLong(), k -> new ArrayList()).add(new Sig(p.getX(), p.getY(), p.getZ(), w, 8, false));
        }
        return out;
    }

    private void submitScan(WorldChunk chunk, Cfg cfg, List<Sig> entSigs) {
        ExecutorService ex = this.executor;
        if (ex == null || ex.isShutdown() || this.mc.world == null) {
            return;
        }
        long key = chunk.getPos().toLong();
        if (!this.inFlight.add(key)) {
            return;
        }
        this.scanned.add(key);
        ClientWorld world = this.mc.world;
        List<Object> bes = cfg.bes() || cfg.bee() ? new ArrayList(chunk.getBlockEntities().values()) : List.of();
        ArrayList<Sig> ents = new ArrayList<Sig>(entSigs);
        try {
            ex.submit(() -> {
                try {
                    ChunkSig res = this.scan(chunk, world, cfg, bes, ents);
                    long now = System.currentTimeMillis();
                    if (res.targets().isEmpty()) {
                        this.targetMap.remove(key);
                    } else {
                        this.targetMap.put(key, res.targets());
                    }
                    if (this.naturalChunks.contains(key)) {
                        this.results.remove(key);
                        this.track.remove(key);
                        return;
                    }
                    if (res.valid()) {
                        this.results.put(key, res);
                        this.track.compute(key, (k, v) -> {
                            long[] lArray;
                            if (v == null) {
                                long[] lArray2 = new long[2];
                                lArray2[0] = now;
                                lArray = lArray2;
                                lArray2[1] = now;
                            } else {
                                long[] lArray3 = new long[2];
                                lArray3[0] = v[0];
                                lArray = lArray3;
                                lArray3[1] = now;
                            }
                            return lArray;
                        });
                        this.dirty = true;
                    }
                }
                catch (Throwable t) {
                    this.scanned.remove(key);
                }
                finally {
                    this.inFlight.remove(key);
                }
            });
        }
        catch (Throwable t) {
            this.scanned.remove(key);
            this.inFlight.remove(key);
        }
    }

    private ChunkSig scan(WorldChunk chunk, ClientWorld world, Cfg cfg, List<BlockEntity> bes, List<Sig> ents) {
        boolean dripForced;
        boolean llamaForced;
        boolean pillForced;
        boolean beeForced;
        int i;
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        List<Sig> sigs = new ArrayList<Sig>();
        boolean natural = false;
        boolean hard = false;
        int[] markers = new int[8];
        int[] surf = new int[256];
        Arrays.fill(surf, Integer.MIN_VALUE);
        ArrayList<Sig> beeSigs = new ArrayList<Sig>();
        ArrayList<Sig> dripSigs = new ArrayList<Sig>();
        HashSet<Long> dripSeen = new HashSet<Long>();
        HashSet<Long> beeSeen = new HashSet<Long>();
        ArrayList<Target> targets = new ArrayList<Target>();
        HashMap<Long, Boolean> artifactMemo = new HashMap<Long, Boolean>();
        HashSet<Long> seenPos = new HashSet<Long>();
        for (BlockEntity be : bes) {
            float w;
            if (be instanceof BeehiveBlockEntity) {
                if (!cfg.bee() || beeSigs.size() >= 16) continue;
                BlockPos bp = be.getPos();
                if (!SusChunkDebugV2.isValidBeehive(be.getCachedState(), bp.getY(), cfg) || cfg.beeUnderground() && !SusChunkDebugV2.underground(chunk, surf, bp.getX(), bp.getY(), bp.getZ(), cfg) || !beeSeen.add(bp.asLong())) continue;
                beeSigs.add(new Sig(bp.getX(), bp.getY(), bp.getZ(), 1.0f, 128, false));
                continue;
            }
            if (!cfg.bes() || sigs.size() >= 500) continue;
            if (be instanceof TrialSpawnerBlockEntity || be instanceof VaultBlockEntity || be instanceof SculkShriekerBlockEntity || be instanceof SculkCatalystBlockEntity || be instanceof SculkSensorBlockEntity) {
                hard = true;
                continue;
            }
            if (be instanceof MobSpawnerBlockEntity) {
                natural = true;
                continue;
            }
            BlockPos p = be.getPos();
            if (!SusChunkDebugV2.underground(chunk, surf, p.getX(), p.getY(), p.getZ(), cfg) || (w = SusChunkDebugV2.beWeight(be)) <= 0.0f) continue;
            boolean farm = be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity;
            boolean prone = be instanceof DispenserBlockEntity;
            sigs.add(new Sig(p.getX(), p.getY(), p.getZ(), w, farm ? 2 : 1, prone));
            seenPos.add(p.asLong());
            if (!farm || targets.size() >= 64 || cfg.filter() && SusChunkDebugV2.structureArtifact(world, p.getX(), p.getY(), p.getZ(), artifactMemo)) continue;
            targets.add(new Target(p.getX(), p.getY(), p.getZ(), be.getCachedState().getBlock()));
        }
        ArrayList<Sig> kelpSigs = new ArrayList<Sig>();
        ArrayList<Sig> vineSigs = new ArrayList<Sig>();
        ArrayList<Sig> bambooSigs = new ArrayList<Sig>();
        ArrayList<Sig> caveSigs = new ArrayList<Sig>();
        int caveYoung = 0;
        ArrayList<Sig> rotSigs = new ArrayList<Sig>();
        int kelpYoung = 0;
        if (cfg.blocks() || cfg.kelp() || cfg.vines() || cfg.rot() || cfg.bamboo() || cfg.cave() || cfg.bee() || cfg.drip()) {
            BlockPos.Mutable mp = new BlockPos.Mutable();
            ChunkSection[] sections = chunk.getSectionArray();
            int bottom = chunk.getBottomY();
            for (i = 0; i < sections.length; ++i) {
                boolean beeInRange;
                ChunkSection s = sections[i];
                int baseY = bottom + i * 16;
                if (s == null || s.isEmpty()) continue;
                boolean secInRange = baseY + 15 >= cfg.minY() && baseY <= cfg.maxY();
                boolean vineInRange = cfg.vines() && baseY + 15 >= cfg.vineLo() && baseY <= cfg.vineHi();
                boolean bl = beeInRange = cfg.bee() && baseY + 15 >= cfg.beeLo() && baseY <= cfg.beeHi();
                if (!secInRange && !cfg.kelp() && !vineInRange && !beeInRange && !cfg.cave() && !cfg.drip() && (!cfg.bamboo() || cfg.bambooUnder()) || !s.hasAny(st -> {
                    Block b = st.getBlock();
                    return cfg.kelp() && b == Blocks.KELP || vineInRange && b == Blocks.VINE || beeInRange && (b == Blocks.BEEHIVE || b == Blocks.BEE_NEST) || cfg.drip() && b == Blocks.POINTED_DRIPSTONE || cfg.bamboo() && b == Blocks.BAMBOO || cfg.cave() && b == Blocks.CAVE_VINES || secInRange && cfg.rot() && b == Blocks.DEEPSLATE || secInRange && cfg.blocks() && SusChunkDebugV2.infoOf(b) != NONE;
                })) continue;
                for (int x = 0; x < 16; ++x) {
                    for (int y = 0; y < 16; ++y) {
                        int wy = baseY + y;
                        for (int z = 0; z < 16; ++z) {
                            BInfo info;
                            BlockState st2 = s.getBlockState(x, y, z);
                            Block b = st2.getBlock();
                            if (cfg.kelp() && b == Blocks.KELP) {
                                if (st2.contains((Property)Properties.AGE_25) && (Integer)st2.get((Property)Properties.AGE_25) >= 25) {
                                    kelpSigs.add(new Sig(startX + x, wy, startZ + z, 0.5f, 16, false));
                                    continue;
                                }
                                ++kelpYoung;
                                continue;
                            }
                            if (cfg.cave() && b == Blocks.CAVE_VINES) {
                                if (st2.contains((Property)Properties.AGE_25) && (Integer)st2.get((Property)Properties.AGE_25) >= cfg.caveAge()) {
                                    caveSigs.add(new Sig(startX + x, wy, startZ + z, 0.5f, 16, false));
                                    continue;
                                }
                                ++caveYoung;
                                continue;
                            }
                            if (cfg.bamboo() && b == Blocks.BAMBOO) {
                                if (!SusChunkDebugV2.columnGrounded(chunk, mp, startX + x, wy, startZ + z, Blocks.BAMBOO, 24, cfg.bambooHeight()) || cfg.bambooUnder() && !SusChunkDebugV2.underground(chunk, surf, startX + x, wy, startZ + z, cfg)) continue;
                                bambooSigs.add(new Sig(startX + x, wy, startZ + z, 0.5f, 4096, false));
                                continue;
                            }
                            if (cfg.vines() && b == Blocks.VINE) {
                                if (wy < cfg.vineLo() || wy > cfg.vineHi() || !SusChunkDebugV2.vineColumn(chunk, mp, startX + x, wy, startZ + z, cfg)) continue;
                                vineSigs.add(new Sig(startX + x, wy, startZ + z, 0.5f, 2048, false));
                                continue;
                            }
                            if (cfg.bee() && (b == Blocks.BEEHIVE || b == Blocks.BEE_NEST)) {
                                if (beeSigs.size() >= 16 || !SusChunkDebugV2.isValidBeehive(st2, wy, cfg) || cfg.beeUnderground() && !SusChunkDebugV2.underground(chunk, surf, startX + x, wy, startZ + z, cfg) || !beeSeen.add(BlockPos.asLong((int)(startX + x), (int)wy, (int)(startZ + z)))) continue;
                                beeSigs.add(new Sig(startX + x, wy, startZ + z, 1.0f, 128, false));
                                continue;
                            }
                            if (cfg.drip() && b == Blocks.POINTED_DRIPSTONE) {
                                int up;
                                if (dripSigs.size() >= 16 || !SusChunkDebugV2.isDripTip(st2)) continue;
                                int wx = startX + x;
                                int wz = startZ + z;
                                int dn = SusChunkDebugV2.dripDown(chunk, mp, wx, wy, wz);
                                if (dn >= Math.max(cfg.stalactiteMin(), cfg.dripAlert()) && dripSeen.add(BlockPos.asLong((int)wx, (int)(wy - dn + 1), (int)wz))) {
                                    dripSigs.add(new Sig(wx, wy - dn + 1, wz, 1.0f, 1024, false));
                                }
                                if ((up = SusChunkDebugV2.dripUp(chunk, mp, wx, wy, wz)) < Math.max(cfg.stalagmiteMin(), cfg.dripAlert()) || !dripSeen.add(BlockPos.asLong((int)wx, (int)(wy + up - 1), (int)wz))) continue;
                                dripSigs.add(new Sig(wx, wy + up - 1, wz, 1.0f, 1024, false));
                                continue;
                            }
                            if (cfg.rot() && secInRange && b == Blocks.DEEPSLATE && st2.contains((Property)Properties.AXIS) && st2.get((Property)Properties.AXIS) != Direction.Axis.Y) {
                                if (!SusChunkDebugV2.underground(chunk, surf, startX + x, wy, startZ + z, cfg) || !SusChunkDebugV2.buried(world, startX + x, wy, startZ + z)) continue;
                                rotSigs.add(new Sig(startX + x, wy, startZ + z, 2.0f, 32, false));
                                continue;
                            }
                            if (!secInRange || !cfg.blocks() || (info = SusChunkDebugV2.infoOf(b)) == NONE || info.marker() == 0 && seenPos.contains(BlockPos.asLong((int)(startX + x), (int)wy, (int)(startZ + z)))) continue;
                            if (info.marker() < 0) {
                                if (info.marker() == -2) {
                                    hard = true;
                                    continue;
                                }
                                natural = true;
                                continue;
                            }
                            if (info.marker() > 0) {
                                int n = info.marker();
                                markers[n] = markers[n] + 1;
                                continue;
                            }
                            if (sigs.size() >= 500 || !SusChunkDebugV2.underground(chunk, surf, startX + x, wy, startZ + z, cfg)) continue;
                            sigs.add(new Sig(startX + x, wy, startZ + z, info.w(), info.kind(), info.prone()));
                            if (!SusChunkDebugV2.isTargetBlock(b) || targets.size() >= 64 || cfg.filter() && SusChunkDebugV2.structureArtifact(world, startX + x, wy, startZ + z, artifactMemo)) continue;
                            targets.add(new Target(startX + x, wy, startZ + z, b));
                        }
                    }
                }
            }
        }
        if (markers[6] >= 2 || markers[4] >= 12) {
            hard = true;
        }
        if (markers[2] >= 20 || markers[1] >= 16 || markers[3] >= 10 || markers[5] >= 6) {
            natural = true;
        }
        ArrayList<Sig> pillSigs = new ArrayList<Sig>();
        ArrayList<Sig> llamaSigs = new ArrayList<Sig>();
        for (Sig e : ents) {
            if (e.kind() == 256) {
                pillSigs.add(e);
                continue;
            }
            if (e.kind() == 512) {
                llamaSigs.add(e);
                continue;
            }
            if (sigs.size() >= 500) continue;
            sigs.add(e);
        }
        if (natural && cfg.filter()) {
            sigs.removeIf(Sig::prone);
        }
        if (hard && cfg.filter()) {
            this.naturalChunks.add(chunk.getPos().toLong());
            return new ChunkSig(chunk.getPos().toLong(), cx, cz, List.of(), 0.0, 0, false, List.of());
        }
        sigs = SusChunkDebugV2.dropIsolated(sigs);
        boolean baseForced = false;
        if (cfg.baseOn() && targets.size() >= cfg.baseMin()) {
            baseForced = true;
            for (i = 0; i < targets.size() && i < 8; ++i) {
                Target t = (Target)targets.get(i);
                sigs.add(new Sig(t.x(), t.y(), t.z(), 0.5f, 2, false));
            }
        }
        boolean bl = beeForced = !beeSigs.isEmpty();
        if (beeForced) {
            sigs.addAll(beeSigs);
        }
        boolean bl2 = pillForced = cfg.pillager() && pillSigs.size() >= cfg.pillagerMin();
        if (pillForced) {
            sigs.addAll(pillSigs);
        }
        boolean bl3 = llamaForced = cfg.llama() && llamaSigs.size() >= cfg.llamaMin();
        if (llamaForced) {
            sigs.addAll(llamaSigs);
        }
        boolean bl4 = dripForced = cfg.drip() && !dripSigs.isEmpty();
        if (dripForced) {
            sigs.addAll(dripSigs);
        }
        double score = 0.0;
        int kinds = 0;
        int farmSigs = 0;
        for (Sig sg : sigs) {
            score += (double)sg.w();
            kinds |= sg.kind();
            if (sg.kind() != 2) continue;
            ++farmSigs;
        }
        boolean valid = sigs.size() >= cfg.minSignals() && score >= cfg.need();
        boolean cropFlag = false;
        if (cfg.kelp() && kelpSigs.size() >= cfg.kelpMin() && kelpYoung <= kelpSigs.size()) {
            for (Sig k : kelpSigs) {
                if (sigs.size() >= 800) break;
                sigs.add(k);
                score += (double)k.w();
                ++farmSigs;
            }
            cropFlag = true;
        }
        if (cfg.bamboo() && bambooSigs.size() >= cfg.bambooMin()) {
            for (Sig k : bambooSigs) {
                if (sigs.size() >= 800) break;
                sigs.add(k);
                score += (double)k.w();
                ++farmSigs;
            }
            cropFlag = true;
        }
        if (cfg.cave() && caveSigs.size() >= cfg.caveMin() && (!cfg.caveYoungCancel() || caveYoung <= caveSigs.size())) {
            for (Sig k : caveSigs) {
                if (sigs.size() >= 800) break;
                sigs.add(k);
                score += (double)k.w();
                ++farmSigs;
            }
            cropFlag = true;
        }
        if (cfg.vines() && vineSigs.size() >= cfg.vineMin()) {
            for (Sig v : vineSigs) {
                if (sigs.size() >= 800) break;
                sigs.add(v);
                score += (double)v.w();
                ++farmSigs;
            }
            cropFlag = true;
        }
        if (cfg.rot() && rotSigs.size() >= cfg.rotMin()) {
            for (Sig r : rotSigs) {
                if (sigs.size() >= 800) break;
                sigs.add(r);
                score += (double)r.w();
            }
            cropFlag = true;
        }
        if (cropFlag || baseForced || beeForced || pillForced || llamaForced || dripForced) {
            valid = true;
            if (score < cfg.need()) {
                score = cfg.need();
            }
        }
        return new ChunkSig(chunk.getPos().toLong(), cx, cz, sigs, score, farmSigs, valid, targets);
    }

    private static boolean isTargetBlock(Block b) {
        return TARGET_CACHE.computeIfAbsent(b, k -> {
            String id = Registries.BLOCK.getId(k).getPath();
            return id.equals("observer") || id.equals("piston") || id.equals("sticky_piston") || id.equals("note_block") || id.equals("hopper") || id.equals("dropper") || id.equals("dispenser");
        });
    }

    private static boolean structureArtifact(ClientWorld world, int x, int y, int z, Map<Long, Boolean> memo) {
        long ck = SusChunkDebugV2.cell(x >> 2, y >> 2, z >> 2);
        Boolean cached = memo.get(ck);
        if (cached != null) {
            return cached;
        }
        boolean found = false;
        BlockPos.Mutable mp = new BlockPos.Mutable();
        block0: for (int dx = -6; dx <= 6; dx += 2) {
            for (int dy = -4; dy <= 4; dy += 2) {
                for (int dz = -6; dz <= 6; dz += 2) {
                    BInfo info;
                    BlockState st = world.getBlockState((BlockPos)mp.set(x + dx, y + dy, z + dz));
                    if (st.isAir() || (info = SusChunkDebugV2.infoOf(st.getBlock())).marker() != -2 && info.marker() != 6 && info.marker() != 4) continue;
                    found = true;
                    break block0;
                }
            }
        }
        memo.put(ck, found);
        return found;
    }

    private static boolean buried(ClientWorld world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        for (Direction d : Direction.values()) {
            if (!world.getBlockState(pos.offset(d)).isAir()) continue;
            return false;
        }
        return true;
    }

    private static boolean underground(WorldChunk chunk, int[] surf, int wx, int wy, int wz, Cfg cfg) {
        if (wy < cfg.minY() || wy > cfg.maxY()) {
            return false;
        }
        int idx = (wz & 0xF) << 4 | wx & 0xF;
        int top = surf[idx];
        if (top == Integer.MIN_VALUE) {
            try {
                top = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, wx & 0xF, wz & 0xF);
            }
            catch (Throwable t) {
                return false;
            }
            surf[idx] = top;
        }
        return wy <= top - cfg.depth();
    }

    private static List<Sig> dropIsolated(List<Sig> in) {
        if (in.size() < 2) {
            return in.size() == 1 ? new ArrayList() : in;
        }
        HashMap<Long, List> grid = new HashMap<Long, List>();
        for (Sig s : in) {
            grid.computeIfAbsent(SusChunkDebugV2.cell(s.x() >> 3, s.y() >> 3, s.z() >> 3), k -> new ArrayList()).add(s);
        }
        double lim = 100.0;
        ArrayList<Sig> out = new ArrayList<Sig>(in.size());
        for (Sig s : in) {
            boolean friend = false;
            int gx = s.x() >> 3;
            int gy = s.y() >> 3;
            int gz = s.z() >> 3;
            block2: for (int dx = -1; dx <= 1; ++dx) {
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        List l = (List)grid.get(SusChunkDebugV2.cell(gx + dx, gy + dy, gz + dz));
                        if (l == null) continue;
                        for (Sig o : l) {
                            double ddz;
                            double ddy;
                            double ddx;
                            if (o == s || !((ddx = (double)(o.x() - s.x())) * ddx + (ddy = (double)(o.y() - s.y())) * ddy + (ddz = (double)(o.z() - s.z())) * ddz <= lim)) continue;
                            friend = true;
                            break block2;
                        }
                    }
                }
            }
            if (!friend) continue;
            out.add(s);
        }
        return out;
    }

    private static long cell(int x, int y, int z) {
        return (long)(x & 0x1FFFFF) << 42 | (long)(y & 0x1FFFFF) << 21 | (long)(z & 0x1FFFFF);
    }

    private static float beWeight(BlockEntity be) {
        if (be instanceof HopperBlockEntity) {
            return 1.5f;
        }
        if (be instanceof DispenserBlockEntity) {
            return 1.0f;
        }
        if (be instanceof ShulkerBoxBlockEntity) {
            return 0.9f;
        }
        if (be instanceof EnderChestBlockEntity) {
            return 0.4f;
        }
        return 0.0f;
    }

    private static BInfo infoOf(Block b) {
        return INFO_CACHE.computeIfAbsent(b, SusChunkDebugV2::computeInfo);
    }

    private static BInfo computeInfo(Block b) {
        String id;
        switch (id = Registries.BLOCK.getId((Object)b).getPath()) {
            case "cobweb": 
            case "end_portal_frame": {
                return new BInfo(0.0f, 0, false, -1);
            }
            case "sculk_shrieker": 
            case "sculk_catalyst": 
            case "reinforced_deepslate": 
            case "trial_spawner": 
            case "vault": {
                return new BInfo(0.0f, 0, false, -2);
            }
        }
        if (id.equals("stone_bricks") || id.equals("mossy_stone_bricks") || id.equals("cracked_stone_bricks") || id.equals("chiseled_stone_bricks") || id.equals("stone_brick_stairs") || id.equals("stone_brick_slab")) {
            return new BInfo(0.0f, 0, false, 1);
        }
        if (id.equals("sculk") || id.equals("sculk_vein") || id.equals("sculk_sensor") || id.equals("calibrated_sculk_sensor")) {
            return new BInfo(0.0f, 0, false, 6);
        }
        if (id.contains("deepslate_brick") || id.contains("deepslate_tile") || id.equals("chiseled_deepslate")) {
            return new BInfo(0.0f, 0, false, 2);
        }
        if (id.equals("oak_planks") || id.equals("oak_fence") || id.equals("rail")) {
            return new BInfo(0.0f, 0, false, 3);
        }
        if (id.contains("tuff_brick") || id.equals("chiseled_tuff") || id.contains("polished_tuff") || id.contains("copper_bulb") || id.contains("copper_grate") || id.contains("copper_door") || id.contains("copper_trapdoor") || id.equals("heavy_core")) {
            return new BInfo(0.0f, 0, false, 4);
        }
        if (id.equals("mossy_cobblestone")) {
            return new BInfo(0.0f, 0, false, 5);
        }
        switch (id) {
            case "observer": {
                return new BInfo(1.0f, 2, false, 0);
            }
            case "piston": 
            case "sticky_piston": {
                return new BInfo(0.8f, 2, false, 0);
            }
            case "note_block": {
                return new BInfo(0.5f, 4, false, 0);
            }
            case "hopper": {
                return new BInfo(1.5f, 2, false, 0);
            }
            case "dropper": 
            case "dispenser": {
                return new BInfo(1.0f, 2, true, 0);
            }
            case "ender_chest": {
                return new BInfo(0.4f, 1, false, 0);
            }
        }
        if (id.equals("shulker_box") || id.endsWith("_shulker_box")) {
            return new BInfo(0.9f, 1, false, 0);
        }
        return NONE;
    }

    private static boolean columnGrounded(WorldChunk chunk, BlockPos.Mutable mp, int wx, int wy, int wz, Block kind, int cap, int minLen) {
        int len;
        BlockState below = chunk.getBlockState((BlockPos)mp.set(wx, wy - 1, wz));
        if (below.isAir() || below.isOf(kind)) {
            return false;
        }
        for (len = 1; len < cap && chunk.getBlockState((BlockPos)mp.set(wx, wy + len, wz)).isOf(kind); ++len) {
        }
        return len >= minLen;
    }

    private static boolean vineColumn(WorldChunk chunk, BlockPos.Mutable mp, int wx, int wy, int wz, Cfg cfg) {
        BlockState below = chunk.getBlockState((BlockPos)mp.set(wx, wy - 1, wz));
        if (cfg.vineOnce() && below.isOf(Blocks.VINE)) {
            return false;
        }
        if (cfg.vineGround() && (below.isAir() || below.isOf(Blocks.VINE))) {
            return false;
        }
        int top = chunk.getBottomY() + chunk.getHeight() - 1;
        int len = 1;
        for (int yy = wy + 1; yy <= top && len < cfg.vineLen() && chunk.getBlockState((BlockPos)mp.set(wx, yy, wz)).isOf(Blocks.VINE); ++len, ++yy) {
        }
        return len >= cfg.vineLen();
    }

    private static boolean isDripTip(BlockState st) {
        return st.contains((Property)PointedDripstoneBlock.THICKNESS) && ((Thickness)st.get((Property)PointedDripstoneBlock.THICKNESS)).name().contains("TIP");
    }

    private static int dripDown(WorldChunk chunk, BlockPos.Mutable mp, int wx, int wy, int wz) {
        int minY = chunk.getBottomY();
        int len = 0;
        for (int yy = wy; yy >= minY && chunk.getBlockState((BlockPos)mp.set(wx, yy, wz)).isOf(Blocks.POINTED_DRIPSTONE); --yy) {
            ++len;
        }
        return len;
    }

    private static int dripUp(WorldChunk chunk, BlockPos.Mutable mp, int wx, int wy, int wz) {
        int topY;
        try {
            topY = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, wx & 0xF, wz & 0xF);
        }
        catch (Throwable t) {
            topY = chunk.getBottomY() + chunk.getHeight();
        }
        int len = 0;
        for (int yy = wy; yy < topY && chunk.getBlockState((BlockPos)mp.set(wx, yy, wz)).isOf(Blocks.POINTED_DRIPSTONE); ++yy) {
            ++len;
        }
        return len;
    }

    private static boolean isValidBeehive(BlockState state, int y, Cfg cfg) {
        boolean nest;
        if (y < cfg.beeLo() || y > cfg.beeHi()) {
            return false;
        }
        Block b = state.getBlock();
        boolean hive = cfg.beeHives() && b == Blocks.BEEHIVE;
        boolean bl = nest = cfg.beeNests() && b == Blocks.BEE_NEST;
        if (!hive && !nest) {
            return false;
        }
        if (!state.contains((Property)Properties.HONEY_LEVEL)) {
            return false;
        }
        int lvl = (Integer)state.get((Property)Properties.HONEY_LEVEL);
        return lvl >= 0 && lvl <= 5 && (cfg.beeMask() & 1 << lvl) != 0;
    }

    private void rebuildZones(long now) {
        Object t;
        double need = SusChunkDebugV2.num(this.sensitivity);
        int rad = SusChunkDebugV2.num(this.mergeRadius);
        int margin = SusChunkDebugV2.bool(this.structureFilter) ? SusChunkDebugV2.num(this.structureMargin) : -1;
        HashMap<Long, ChunkSig> cand = new HashMap<Long, ChunkSig>();
        for (ChunkSig cs : this.results.values()) {
            t = this.track.get(cs.key());
            if (t == null || this.nearNatural(cs.cx(), cs.cz(), margin) || !(cs.score() >= need)) continue;
            cand.put(cs.key(), cs);
        }
        Iterator<Object> iterator = this.amethystFlagged().iterator();
        while (iterator.hasNext()) {
            int ccz;
            long k = (Long)iterator.next();
            int ccx = ChunkPos.getPackedX((long)k);
            if (this.nearNatural(ccx, ccz = ChunkPos.getPackedZ((long)k), margin)) continue;
            ChunkSig prev = (ChunkSig)cand.get(k);
            if (prev != null && prev.score() >= need) {
                ArrayList<Sig> merged = new ArrayList<Sig>(prev.sigs());
                merged.add(new Sig(ccx * 16, 0, ccz * 16, 0.5f, 64, false));
                merged.add(new Sig(ccx * 16 + 15, 0, ccz * 16 + 15, 0.5f, 64, false));
                cand.put(k, new ChunkSig(k, ccx, ccz, merged, prev.score() + 1.0, prev.farmSigs(), true, prev.targets()));
                continue;
            }
            cand.put(k, new ChunkSig(k, ccx, ccz, List.of(new Sig(ccx * 16, 0, ccz * 16, 0.5f, 64, false), new Sig(ccx * 16 + 15, 0, ccz * 16 + 15, 0.5f, 64, false)), Math.max(need, 1.0), 0, true, List.of()));
        }
        if (cand.isEmpty()) {
            this.primary = null;
            this.zoneContinuing = false;
            this.rotBlocks = Collections.emptyList();
            this.zones = Collections.emptyList();
            return;
        }
        ArrayList<Zone> all = new ArrayList<Zone>();
        HashSet<Long> seen = new HashSet<Long>();
        t = cand.values().iterator();
        while (t.hasNext()) {
            ChunkSig start = (ChunkSig)t.next();
            if (!seen.add(start.key())) continue;
            ArrayDeque<ChunkSig> q = new ArrayDeque<ChunkSig>();
            q.add(start);
            ArrayList members = new ArrayList();
            while (!q.isEmpty()) {
                ChunkSig c = (ChunkSig)q.poll();
                members.add(c);
                for (int dx = -rad; dx <= rad; ++dx) {
                    for (int dz = -rad; dz <= rad; ++dz) {
                        ChunkSig nb = (ChunkSig)cand.get(ChunkPos.toLong((int)(c.cx() + dx), (int)(c.cz() + dz)));
                        if (nb == null || !seen.add(nb.key())) continue;
                        q.add(nb);
                    }
                }
            }
            all.add(this.makeZone(members));
        }
        Zone best = null;
        for (Zone z : all) {
            if (best != null && !(z.score() > best.score())) continue;
            best = z;
        }
        Zone keep = null;
        Zone prev = this.primary;
        if (prev != null) {
            for (Zone z : all) {
                boolean overlap = false;
                for (long k : z.chunkKeys()) {
                    if (!prev.chunkKeys().contains(k)) continue;
                    overlap = true;
                    break;
                }
                double dx = z.x() - prev.x();
                double dz = z.z() - prev.z();
                if (!overlap && !(dx * dx + dz * dz <= 4096.0) || keep != null && !(z.score() > keep.score())) continue;
                keep = z;
            }
        }
        Zone chosen = best;
        if (keep != null && (best == null || best.score() < keep.score() * 2.0)) {
            chosen = keep;
        }
        this.zoneContinuing = keep != null && chosen == keep;
        this.primary = chosen;
        boolean fresh = true;
        for (long k : chosen.chunkKeys()) {
            if (!this.announced.contains(k)) continue;
            fresh = false;
            break;
        }
        this.announced.addAll(chosen.chunkKeys());
        if (fresh && SusChunkDebugV2.bool(this.chatCoords)) {
            Notify.chat("SusChunkDebugV2", "Sus chunk detected at X: " + Math.round(chosen.cx()) + "  Z: " + Math.round(chosen.cz()) + " (" + (chosen.farm() ? "Farm" : "Base") + ")");
        }
        this.zones = List.of(chosen);
        ArrayList<int[]> rots = new ArrayList<int[]>();
        for (long k : chosen.chunkKeys()) {
            ChunkSig cs = this.results.get(k);
            if (cs == null) continue;
            for (Sig sg : cs.sigs()) {
                if (sg.kind() != 32 || rots.size() >= 64) continue;
                rots.add(new int[]{sg.x(), sg.y(), sg.z()});
            }
        }
        this.rotBlocks = rots;
    }

    private boolean nearNatural(int cx, int cz, int margin) {
        if (margin < 0 || this.naturalChunks.isEmpty()) {
            return false;
        }
        for (int dx = -margin; dx <= margin; ++dx) {
            for (int dz = -margin; dz <= margin; ++dz) {
                if (!this.naturalChunks.contains(ChunkPos.toLong((int)(cx + dx), (int)(cz + dz)))) continue;
                return true;
            }
        }
        return false;
    }

    private static boolean isSpotKind(int kind) {
        return kind == 2048 || kind == 4096 || kind == 128 || kind == 512 || kind == 1024;
    }

    private Zone makeZone(List<ChunkSig> members) {
        double sx = 0.0;
        double sz = 0.0;
        double sw = 0.0;
        double totalScore = 0.0;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        int farmSigs = 0;
        ChunkSig anchor = members.get(0);
        boolean hasSpot = false;
        for (ChunkSig cs : members) {
            for (Sig s : cs.sigs()) {
                if (!SusChunkDebugV2.isSpotKind(s.kind())) continue;
                hasSpot = true;
                break;
            }
            if (!hasSpot) continue;
            break;
        }
        ArrayList<Long> keys = new ArrayList<Long>();
        for (ChunkSig cs : members) {
            keys.add(cs.key());
            farmSigs += cs.farmSigs();
            totalScore += cs.score();
            if (cs.cx() < anchor.cx() || cs.cx() == anchor.cx() && cs.cz() < anchor.cz()) {
                anchor = cs;
            }
            for (Sig s : cs.sigs()) {
                if (hasSpot && !SusChunkDebugV2.isSpotKind(s.kind())) continue;
                sx += ((double)s.x() + 0.5) * (double)s.w();
                sz += ((double)s.z() + 0.5) * (double)s.w();
                sw += (double)s.w();
                minX = Math.min(minX, s.x());
                maxX = Math.max(maxX, s.x());
                minZ = Math.min(minZ, s.z());
                maxZ = Math.max(maxZ, s.z());
                lo = Math.min(lo, s.y());
                hi = Math.max(hi, s.y());
            }
        }
        if (sw <= 0.0 || lo == Integer.MAX_VALUE) {
            return new Zone(anchor.key(), (double)anchor.cx() * 16.0 + 8.0, (double)anchor.cz() * 16.0 + 8.0, 0, 0, 0, 0, false, keys, totalScore, anchor.cx() * 16, anchor.cz() * 16, anchor.cx() * 16 + 15, anchor.cz() * 16 + 15);
        }
        return new Zone(anchor.key(), sx / sw, sz / sw, lo, hi, maxX - minX, maxZ - minZ, farmSigs >= 4, keys, totalScore, minX, minZ, maxX, maxZ);
    }

    private static Color rgba(SettingColor c, double a) {
        return new Color(c.r, c.g, c.b, Math.max(0, Math.min(255, (int)Math.round(a))));
    }

    private void applyStyle() {
        SettingColor c = this.susColor;
        this.style.r = c.r;
        this.style.g = c.g;
        this.style.b = c.b;
        this.style.width = SusChunkDebugV2.num(this.boxWidth);
        this.style.length = SusChunkDebugV2.num(this.boxLength);
        this.style.fillAlpha = SusChunkDebugV2.num(this.fillAlpha);
        this.style.lineAlpha = SusChunkDebugV2.num(this.lineAlpha);
        this.style.rectY = SusChunkDebugV2.num(this.renderY);
        this.style.top = SusChunkDebugV2.num(this.pillarTop);
        this.style.pillar = SusChunkDebugV2.bool(this.pillars);
        this.style.tracer = SusChunkDebugV2.bool(this.zoneTracer);
        this.style.hideDistance = SusChunkDebugV2.num(this.hideDistance);
    }

    private void renderWorld(Render3DEvent event) {
        if (this.mc.world == null) {
            return;
        }
        this.applyStyle();
        this.renderZones(event, this.zones);
    }

    private void renderZones(Render3DEvent event, List<Zone> current) {
        Color l;
        SettingColor base = this.susColor;
        Color none = new Color(0, 0, 0, 0);
        List<int[]> rots = this.rotBlocks;
        if (SusChunkDebugV2.bool(this.rotatedEsp)) {
            Color f = SusChunkDebugV2.rgba(base, 70.0);
            l = SusChunkDebugV2.rgba(base, 255.0);
            for (int[] b : rots) {
                event.renderer.box((double)b[0], (double)b[1], (double)b[2], (double)(b[0] + 1), (double)(b[1] + 1), (double)(b[2] + 1), f, l, ShapeMode.Both, 0);
            }
        }
        if (SusChunkDebugV2.bool(this.rotatedTracers) && this.mc.player != null && !rots.isEmpty()) {
            Vec3d start = event.center;
            l = SusChunkDebugV2.rgba(base, 255.0);
            for (int[] b : rots) {
                event.renderer.line(start.x, start.y, start.z, (double)b[0] + 0.5, (double)b[1] + 0.5, (double)b[2] + 0.5, l);
            }
        }
        this.fx.begin();
        double y = SusChunkDebugV2.num(this.renderY);
        double radius = this.zoneContinuing ? 1.0E9 : 48.0;
        for (Zone z : current) {
            this.fx.touch(z.cx(), z.cz(), y, radius);
        }
        this.fx.render(event, this.style);
    }

    @Override
    public String info() {
        return String.valueOf(this.zones.size());
    }

    private record AmScan(int done, int pending, int radius) {
    }

    private record Cfg(int minY, int maxY, int depth, int minSignals, double need, boolean bes, boolean blocks, boolean kelp, int kelpMin, boolean vines, int vineMin, int vineLen, boolean filter, boolean rot, int rotMin, boolean baseOn, int baseMin, boolean bee, boolean beeUnderground, boolean pillager, int pillagerMin, boolean bamboo, int bambooMin, boolean bambooUnder, boolean cave, int caveMin, int bambooHeight, int vineLo, int vineHi, int beeLo, int beeHi, boolean beeHives, boolean beeNests, int beeMask, boolean llama, int llamaMin, int llamaDist, boolean drip, int stalactiteMin, int stalagmiteMin, int dripAlert, int caveAge, boolean caveYoungCancel, boolean vineGround, boolean vineOnce) {
    }

    private record Zone(long key, double x, double z, int minY, int maxY, int spanX, int spanZ, boolean farm, List<Long> chunkKeys, double score, int x1, int z1, int x2, int z2) {
        double cx() {
            return (double)(this.x1 + this.x2 + 1) / 2.0;
        }

        double cz() {
            return (double)(this.z1 + this.z2 + 1) / 2.0;
        }
    }

    private record BInfo(float w, int kind, boolean prone, int marker) {
    }

    private record Target(int x, int y, int z, Block block) {
    }

    private record Sig(int x, int y, int z, float w, int kind, boolean prone) {
    }

    private record ChunkSig(long key, int cx, int cz, List<Sig> sigs, double score, int farmSigs, boolean valid, List<Target> targets) {
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
