package abcxyz.module.misc;

import abcxyz.compat.Color;
import abcxyz.compat.Render3DEvent;
import abcxyz.compat.ShapeMode;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Packet mining: you click a block once, the module sends the "start digging" packet and counts the progress itself with
 * the best tool in your hotbar, then sends "stop digging" the moment the block is ready. With Rebreak it keeps sending
 * "stop" while the block comes back (some servers replace it), so the block breaks again without a new click.
 * The click is caught in ClientPlayerInteractionManagerMixin. Creative mode is left to the game.
 */
public class SpeedMine extends Module {
    private final NumSetting range = add(new NumSetting("Range", 4.5, 1.0, 6.0, 0.1));
    private final BoolSetting autoSwitch = add(new BoolSetting("AutoSwitch", true));
    private final NumSetting rebreakDelay = add(new NumSetting("RebreakDelay", 2, 1, 10, 1));
    private final BoolSetting rebreak = add(new BoolSetting("Rebreak", true)).with(rebreakDelay);
    private final BoolSetting render = add(new BoolSetting("Render", true));

    private BlockPos target;
    private Direction face = Direction.UP;
    private float progress;
    private boolean stopSent;
    private int sinceStop;
    private int airTicks;

    public SpeedMine() {
        super("SpeedMine", "Mine blocks with packets: one click, best tool, instant stop", Category.Misc);
        autoSwitch.label = "Auto switch";
        rebreak.label = "Rebreak";
        rebreakDelay.label = "Rebreak delay (ticks)";
    }

    @Override
    public String info() {
        return target == null ? "" : String.valueOf(Math.min(100, Math.round(progress * 100.0f))) + "%";
    }

    @Override
    public void onDisable() {
        abort();
    }

    private boolean inRange(BlockPos pos) {
        double r = range.d();
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= r * r;
    }

    private void send(PlayerActionC2SPacket.Action action, BlockPos pos, Direction dir) {
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, pos, dir, 0));
        }
    }

    /** The player clicked a block. @return true when the module took over (the game then does nothing). */
    public boolean start(BlockPos pos, Direction dir) {
        if (mc.player == null || mc.world == null || mc.player.isCreative() || mc.player.isSpectator()) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir() || state.getHardness(mc.world, pos) < 0.0f || !inRange(pos)) return false;
        if (pos.equals(target)) return true;
        abort();
        target = pos.toImmutable();
        face = dir == null ? Direction.UP : dir;
        progress = 0.0f;
        stopSent = false;
        sinceStop = 0;
        airTicks = 0;
        send(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, face);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private void abort() {
        if (target != null && !stopSent && mc.player != null) {
            send(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, target, face);
        }
        clear();
    }

    private void clear() {
        target = null;
        progress = 0.0f;
        stopSent = false;
        sinceStop = 0;
        airTicks = 0;
    }

    /** Breaking progress of one tick with the best hotbar tool (or the held one). Writes the slot to bestSlot. */
    private int bestSlot;

    private float bestDelta(BlockState state) {
        PlayerInventory inventory = mc.player.getInventory();
        int old = inventory.selectedSlot;
        bestSlot = old;
        float best = state.calcBlockBreakingDelta(mc.player, mc.world, target);
        if (autoSwitch.on()) {
            for (int slot = 0; slot < 9; slot++) {
                inventory.selectedSlot = slot;
                float delta = state.calcBlockBreakingDelta(mc.player, mc.world, target);
                if (delta > best) {
                    best = delta;
                    bestSlot = slot;
                }
            }
            inventory.selectedSlot = old;
        }
        return best;
    }

    private void sendStop(int slot) {
        int held = mc.player.getInventory().selectedSlot;
        boolean swap = autoSwitch.on() && slot != held && mc.getNetworkHandler() != null;
        if (swap) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        send(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, target, face);
        if (swap) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(held));
        mc.player.swingHand(Hand.MAIN_HAND);
        stopSent = true;
        sinceStop = 0;
    }

    @Override
    public void onTick() {
        if (target == null) return;
        if (mc.player == null || mc.world == null || mc.player.isCreative()) {
            clear();
            return;
        }
        BlockState state = mc.world.getBlockState(target);
        if (state.isAir()) {
            // broken: with Rebreak wait for the block to come back for a little while
            if (stopSent && rebreak.on() && ++airTicks <= 100) return;
            clear();
            return;
        }
        airTicks = 0;
        if (!inRange(target)) {
            abort();
            return;
        }
        if (!stopSent) {
            progress += bestDelta(state);
            if (progress >= 1.0f) sendStop(bestSlot);
            return;
        }
        sinceStop++;
        if (rebreak.on()) {
            if (sinceStop >= rebreakDelay.i()) {
                bestDelta(state);
                sendStop(bestSlot);
            }
        } else if (sinceStop > 20) {
            // the block is still there: start digging again
            progress = 0.0f;
            stopSent = false;
            send(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, target, face);
        }
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        if (!render.on() || target == null || mc.world == null) return;
        float p = Math.max(0.05f, Math.min(1.0f, progress));
        double half = 0.5 * p;
        double cx = target.getX() + 0.5;
        double cy = target.getY() + 0.5;
        double cz = target.getZ() + 0.5;
        int r = Math.round(255.0f * (1.0f - p));
        int g = Math.round(255.0f * p);
        Render3DEvent event = new Render3DEvent(context);
        event.renderer.box(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half,
                new Color(r, g, 40, 70), new Color(r, g, 40, 255), ShapeMode.Both, 0);
        event.flush();
    }
}
