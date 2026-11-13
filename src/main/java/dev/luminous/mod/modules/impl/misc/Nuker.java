package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.impl.render.PlaceRender;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Breaks blocks around the player. Modes and options follow what Meteor's Nuker offers
 * (All / Flatten / Smash, sphere or cube, sorting, delay, blocks per tick) but the code is original.
 */
public class Nuker extends Module {
    public static Nuker INSTANCE;

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.All));
    private final EnumSetting<Shape> shape = add(new EnumSetting<>("Shape", Shape.Sphere));
    private final EnumSetting<Sort> sort = add(new EnumSetting<>("Sort", Sort.Closest));
    private final SliderSetting range = add(new SliderSetting("Range", 4, 1, 6, 0.1));
    private final SliderSetting delay = add(new SliderSetting("Delay", 0, 0, 20, 1).setSuffix("t"));
    private final SliderSetting blocksPerTick = add(new SliderSetting("BlocksPerTick", 1, 1, 10, 1));
    private final BooleanSetting packetMine = add(new BooleanSetting("PacketMine", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", false));
    private final BooleanSetting swing = add(new BooleanSetting("Swing", true));
    private final BooleanSetting render = add(new BooleanSetting("Render", true));

    private int ticks = 0;

    public Nuker() {
        super("Nuker", "Breaks blocks around you", Category.Misc);
        setChinese("范围挖掘");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @Override
    public void onEnable() {
        ticks = 0;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        if (ticks > 0) {
            ticks--;
            return;
        }
        boolean creative = mc.player.isCreative();
        if (packetMine.getValue() && !creative) {
            BlockPos current = PacketMine.getBreakPos();
            if (current != null && !mc.world.isAir(current)) return;
        }

        List<Target> targets = collect(creative);
        if (targets.isEmpty()) return;

        Vec3d eye = mc.player.getEyePos();
        Comparator<Target> order = switch (sort.getValue()) {
            case Closest -> Comparator.comparingDouble(t -> eye.squaredDistanceTo(t.pos.toCenterPos()));
            case Furthest -> Comparator.comparingDouble((Target t) -> eye.squaredDistanceTo(t.pos.toCenterPos())).reversed();
            case TopDown -> Comparator.comparingInt((Target t) -> t.pos.getY()).reversed()
                    .thenComparingDouble(t -> eye.squaredDistanceTo(t.pos.toCenterPos()));
        };
        targets.sort(order);

        int count = 0;
        for (Target t : targets) {
            // in survival only one block can be mined at a time, unless it breaks instantly
            int limit = (creative || t.instant) ? blocksPerTick.getValueInt() : 1;
            if (count >= limit) break;
            breakBlock(t, creative);
            count++;
        }
        ticks = delay.getValueInt();
    }

    private void breakBlock(Target t, boolean creative) {
        if (rotate.getValue()) Alien.ROTATION.snapAt(t.pos.toCenterPos());
        if (creative || t.instant) {
            mc.interactionManager.attackBlock(t.pos, t.side);
        } else if (packetMine.getValue()) {
            PacketMine.INSTANCE.mine(t.pos);
        } else {
            mc.interactionManager.updateBlockBreakingProgress(t.pos, t.side);
        }
        if (rotate.getValue()) Alien.ROTATION.snapBack();
        if (swing.getValue()) mc.player.swingHand(Hand.MAIN_HAND);
        if (render.getValue()) PlaceRender.INSTANCE.create(t.pos);
    }

    private List<Target> collect(boolean creative) {
        List<Target> list = new ArrayList<>();
        Vec3d eye = mc.player.getEyePos();
        double r = range.getValue();
        int reach = (int) Math.ceil(r);
        BlockPos center = BlockPos.ofFloored(eye);
        int feetY = mc.player.getBlockY();

        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Vec3d c = pos.toCenterPos();
                    if (shape.getValue() == Shape.Sphere) {
                        if (eye.squaredDistanceTo(c) > r * r) continue;
                    } else if (Math.abs(c.x - eye.x) > r || Math.abs(c.y - eye.y) > r || Math.abs(c.z - eye.z) > r) {
                        continue;
                    }
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.isAir() || state.getBlock() instanceof FluidBlock) continue;
                    float hardness = state.getHardness(mc.world, pos);
                    if (!creative && (hardness < 0 || PacketMine.unbreakable(pos))) continue;
                    if (mode.getValue() == Mode.Flatten && pos.getY() < feetY) continue;
                    if (mode.getValue() == Mode.Smash && hardness != 0f) continue;
                    Direction side = BlockUtil.getClickSideStrict(pos);
                    if (side == null) continue;
                    list.add(new Target(pos, side, hardness == 0f));
                }
            }
        }
        return list;
    }

    private record Target(BlockPos pos, Direction side, boolean instant) {
    }

    private enum Mode {
        All, Flatten, Smash
    }

    private enum Shape {
        Sphere, Cube
    }

    private enum Sort {
        Closest, Furthest, TopDown
    }
}
