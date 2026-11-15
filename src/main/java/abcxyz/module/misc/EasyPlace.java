package abcxyz.module.misc;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.ModeSetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.render.Render3D;
import abcxyz.schematic.Schematic;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Property;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Places the blocks of a schematic with the correct orientation (observers, dispensers, pistons, hoppers, stairs...).
 * Put .litematic or .nbt files in the "schematics" folder of the game directory, choose one in "File",
 * and it starts by itself: the lowest corner of the schematic goes where you stand when it finishes loading
 * (SetOrigin / Offset move it later). Blocks missing from the hotbar are taken from your inventory.
 * It places what is missing within reach, so walk along the build.
 * For every block it looks for a face to click and a look direction that makes the game itself produce the same
 * block state as in the schematic, then sends that look direction to the server before placing.
 */
public class EasyPlace extends Module {
    private static final Set<String> IGNORED = Set.of("powered", "waterlogged", "lit", "triggered", "power",
            "delay", "locked", "open", "extended", "attached", "occupied", "disarmed", "enabled");
    private static final float[][] LOOKS = {
            {0, 0}, {90, 0}, {180, 0}, {270, 0},
            {0, 90}, {90, 90}, {180, 90}, {270, 90},
            {0, -90}, {90, -90}, {180, -90}, {270, -90}
    };

    private final ModeSetting file = add(new ModeSetting("File", "none", "none").dynamic());
    private final BoolSetting reload = add(new BoolSetting("Reload", false));
    private final BoolSetting setOrigin = add(new BoolSetting("SetOrigin", false));
    private final NumSetting offsetX = add(new NumSetting("OffsetX", 0, -512, 512, 1));
    private final NumSetting offsetY = add(new NumSetting("OffsetY", 0, -64, 320, 1));
    private final NumSetting offsetZ = add(new NumSetting("OffsetZ", 0, -512, 512, 1));
    private final ModeSetting trigger = add(new ModeSetting("PlaceMode", "Auto", "Auto", "UseKey"));
    private final BoolSetting fromInventory = add(new BoolSetting("FromInventory", true));
    private final NumSetting range = add(new NumSetting("Range", 4.5, 1.0, 6.0, 0.1));
    private final NumSetting delay = add(new NumSetting("Delay", 60, 0, 500, 10));
    private final NumSetting perBurst = add(new NumSetting("PerBurst", 1, 1, 4, 1));
    private final BoolSetting render = add(new BoolSetting("Render", true));

    private volatile Schematic schematic = null;
    private volatile boolean loading = false;
    private String loadedName = null;
    private BlockPos origin = null;
    private long lastPlace = 0L;
    private long lastSwap = 0L;
    private int ghostTimer = 0;
    private List<BlockPos> ghosts = new ArrayList<>();

    private record Plan(Direction face, BlockPos neighbor, Vec3d hit, float yaw, float pitch) {
    }

    public EasyPlace() {
        super("EasyPlace", "Places schematic blocks with the right orientation", Category.Misc);
        scanFiles();
    }

    private static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    private void scanFiles() {
        List<String> names = new ArrayList<>();
        Path dir = folder();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).filter(n -> {
                    String l = n.toLowerCase();
                    return l.endsWith(".litematic") || l.endsWith(".nbt");
                }).sorted().forEach(names::add);
            } catch (IOException ignored) {
            }
        }
        if (names.isEmpty()) names.add("none");
        file.setModes(names.toArray(new String[0]));
    }

    private void say(String message) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("\u00a7b[abcxyz] \u00a7f" + message), false);
    }

    @Override
    public String info() {
        Schematic s = schematic;
        return s == null ? (loading ? "loading" : null) : s.name;
    }

    @Override
    public void onEnable() {
        scanFiles();
        if (mc.player != null && file.get().equals("none")) say("Put .litematic / .nbt files in the \"schematics\" folder and pick one in File.");
    }

    private void startLoading(String name) {
        loadedName = name;
        schematic = null;
        origin = null;
        if (name.equals("none")) return;
        loading = true;
        Path path = folder().resolve(name);
        Thread thread = new Thread(() -> {
            try {
                Schematic loaded = Schematic.load(path);
                schematic = loaded;
                mc.execute(() -> say("Loaded " + loaded.name + " (" + loaded.blockCount() + " blocks, "
                        + loaded.sizeX + "x" + loaded.sizeY + "x" + loaded.sizeZ + ")"));
            } catch (Throwable t) {
                mc.execute(() -> say("\u00a7cCould not load " + name + ": " + t.getMessage()));
            } finally {
                loading = false;
            }
        }, "abcxyz-schematic-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void onTick() {
        if (reload.on()) {
            reload.set(false);
            scanFiles();
            loadedName = null;
            say("Reloaded the file list (" + file.modes.length + " entries)");
        }
        if (setOrigin.on()) {
            setOrigin.set(false);
            origin = mc.player.getBlockPos();
            say("Origin set to " + origin.getX() + " " + origin.getY() + " " + origin.getZ());
        }
        String wanted = file.get();
        if (!wanted.equals(loadedName) && !loading) startLoading(wanted);

        Schematic s = schematic;
        if (s == null) return;
        if (origin == null) {
            origin = mc.player.getBlockPos();
            say("Building from " + origin.getX() + " " + origin.getY() + " " + origin.getZ()
                    + " (use SetOrigin or Offset to move it)");
        }
        if (--ghostTimer <= 0) {
            ghostTimer = 10;
            ghosts = findMissing(s, 14, 250);
        }
        if (trigger.is("UseKey") && !mc.options.useKey.isPressed()) return;
        if (mc.currentScreen != null || mc.player.isUsingItem()) return;
        long now = System.currentTimeMillis();
        if (now - lastPlace < delay.d()) return;

        Vec3d eye = mc.player.getEyePos();
        double reach = range.d();
        BlockPos shift = shift();
        int placed = 0;
        for (BlockPos pos : findMissing(s, (int) Math.ceil(reach), 400)) {
            if (placed >= perBurst.i()) break;
            if (pos.getSquaredDistance(eye) > (reach + 1.0) * (reach + 1.0)) continue;
            BlockState target = s.get(pos.subtract(shift));
            if (target != null && tryPlace(pos, target, eye, reach)) placed++;
            if (System.currentTimeMillis() - lastSwap < 5) break;
        }
        if (placed > 0) lastPlace = now;
    }

    private BlockPos shift() {
        return origin.add(offsetX.i(), offsetY.i(), offsetZ.i());
    }

    private static boolean wantsPlacing(BlockState target) {
        if (target.isAir() || target.getBlock() instanceof FluidBlock) return false;
        // the upper half of doors / tall plants and the head of beds are created together with the first half
        if (target.contains(Properties.DOUBLE_BLOCK_HALF) && target.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return false;
        if (target.contains(Properties.BED_PART) && target.get(Properties.BED_PART) == BedPart.HEAD) return false;
        return true;
    }

    private static boolean matches(BlockState state, BlockState target) {
        if (state.getBlock() != target.getBlock()) return false;
        Map<Property<?>, Comparable<?>> have = state.getEntries();
        for (Map.Entry<Property<?>, Comparable<?>> entry : target.getEntries().entrySet()) {
            if (IGNORED.contains(entry.getKey().getName())) continue;
            if (!entry.getValue().equals(have.get(entry.getKey()))) return false;
        }
        return true;
    }

    /** Blocks of the schematic around the player that are still missing and could be placed, lowest first. */
    private List<BlockPos> findMissing(Schematic s, int radius, int limit) {
        List<BlockPos> result = new ArrayList<>();
        if (mc.world == null) return result;
        BlockPos base = mc.player.getBlockPos();
        BlockPos shift = shift();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    BlockState target = s.get(pos.subtract(shift));
                    if (target == null || !wantsPlacing(target)) continue;
                    if (!mc.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                    BlockState current = mc.world.getBlockState(pos);
                    if (matches(current, target)) continue;
                    if (!current.isReplaceable()) continue;
                    result.add(pos);
                }
            }
        }
        Vec3d me = mc.player.getPos();
        result.sort(Comparator.comparingInt(BlockPos::getY).thenComparingDouble(p -> p.getSquaredDistance(me)));
        if (result.size() > limit) return new ArrayList<>(result.subList(0, limit));
        return result;
    }

    private int findHotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    /** Moves the item from the main inventory to a hotbar slot that does not hold something the schematic needs. */
    private void bringToHotbar(Item item) {
        long now = System.currentTimeMillis();
        if (now - lastSwap < 200 || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        if (mc.currentScreen != null) return;
        int from = -1;
        for (int i = 9; i < 36 && from == -1; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) from = i;
        }
        if (from == -1) return;
        Schematic s = schematic;
        int target = -1;
        for (int i = 0; i < 9 && target == -1; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) target = i;
        }
        for (int i = 0; i < 9 && target == -1; i++) {
            Item held = mc.player.getInventory().getStack(i).getItem();
            if (s == null || !s.items.contains(held)) target = i;
        }
        if (target == -1) return;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, from, target, SlotActionType.SWAP, mc.player);
        lastSwap = now;
    }

    private boolean tryPlace(BlockPos pos, BlockState target, Vec3d eye, double reach) {
        Item item = target.getBlock().asItem();
        if (item == Items.AIR) return false;
        int slot = findHotbar(item);
        if (slot < 0) {
            if (fromInventory.on()) bringToHotbar(item);
            return false;
        }
        ItemStack stack = mc.player.getInventory().getStack(slot);
        Plan plan = plan(pos, target, stack, eye, reach);
        if (plan == null) return false;

        int oldSlot = mc.player.getInventory().selectedSlot;
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        boolean onGround = mc.player.isOnGround();
        boolean collision = mc.player.horizontalCollision;
        var network = mc.getNetworkHandler();
        if (network == null) return false;

        if (slot != oldSlot) {
            mc.player.getInventory().selectedSlot = slot;
            network.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
        network.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(plan.yaw(), plan.pitch(), onGround, collision));
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(plan.hit(), plan.face(), plan.neighbor(), false));
        mc.player.swingHand(Hand.MAIN_HAND);
        network.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, onGround, collision));
        if (slot != oldSlot) {
            mc.player.getInventory().selectedSlot = oldSlot;
            network.sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
        }
        return true;
    }

    /**
     * Tries every neighbouring block as support, a few hit heights (for slabs / stairs) and the six axis look directions,
     * and lets the game itself compute the block state the placement would give.
     */
    private Plan plan(BlockPos pos, BlockState target, ItemStack stack, Vec3d eye, double reach) {
        float oldYaw = mc.player.getYaw();
        float oldPitch = mc.player.getPitch();
        try {
            for (Direction toNeighbor : Direction.values()) {
                BlockPos neighbor = pos.offset(toNeighbor);
                BlockState support = mc.world.getBlockState(neighbor);
                if (support.isAir() || support.isReplaceable()) continue;
                Direction face = toNeighbor.getOpposite();
                double[] heights = face.getAxis().isHorizontal() ? new double[]{0.5, 0.25, 0.75} : new double[]{0.5};
                for (double height : heights) {
                    Vec3d center = Vec3d.ofCenter(neighbor);
                    Vec3d hit = face.getAxis().isHorizontal()
                            ? new Vec3d(center.x + face.getOffsetX() * 0.5, neighbor.getY() + height, center.z + face.getOffsetZ() * 0.5)
                            : new Vec3d(center.x, center.y + face.getOffsetY() * 0.5, center.z);
                    if (eye.distanceTo(hit) > reach + 0.5) continue;
                    BlockHitResult result = new BlockHitResult(hit, face, neighbor, false);
                    for (float[] look : LOOKS) {
                        mc.player.setYaw(look[0]);
                        mc.player.setPitch(look[1]);
                        ItemPlacementContext context = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, result);
                        if (!context.canPlace()) continue;
                        BlockState state = target.getBlock().getPlacementState(context);
                        if (state != null && matches(state, target)) {
                            return new Plan(face, neighbor, hit, look[0], look[1]);
                        }
                    }
                }
            }
        } finally {
            mc.player.setYaw(oldYaw);
            mc.player.setPitch(oldPitch);
        }
        return null;
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        if (!render.on() || ghosts.isEmpty()) return;
        Matrix4f m = Render3D.push(context);
        Render3D.begin();
        BufferBuilder b = Render3D.lines();
        for (BlockPos pos : ghosts) {
            Render3D.box(b, m, new Box(pos).contract(0.08), 0xFF55FF55);
        }
        Render3D.flush(b);
        Render3D.end();
        Render3D.pop(context);
    }
}
