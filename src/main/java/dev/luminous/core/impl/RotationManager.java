package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.*;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.path.BaritoneUtil;
import dev.luminous.asm.accessors.IClientPlayerEntity;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.movement.HoleSnap;
import dev.luminous.mod.modules.impl.player.Freecam;
import dev.luminous.mod.modules.settings.enums.SnapBack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class RotationManager implements Wrapper {
    public static final Timer ROTATE_TIMER = new Timer();
    public static Vec3d directionVec = null;
    public static boolean snapBack = false;
    private static float renderPitch;
    private static float renderYawOffset;
    private static float prevRenderPitch;
    private static float prevRenderYawOffset;
    private static float prevRotationYawHead;
    private static float rotationYawHead;
    public float nextYaw;
    public float nextPitch;
    public float rotationYaw;
    public float rotationPitch;
    public float lastYaw;
    public float lastPitch;
    public Vec3d crossHairUpdatePos;
    private int ticksExisted;

    public RotationManager() {
        Alien.EVENT_BUS.subscribe(this);
    }

    @EventListener
    public void onInteract(InteractItemEvent event) {
        if (AntiCheat.INSTANCE.interactRotation.getValue() && mc.player != null) {
            if (event.isPre()) {
                snapAt(mc.player.getYaw(), mc.player.getPitch());
            } else {
                snapBack();
            }
        }
    }

    @EventListener
    public void onInteract(InteractBlockEvent event) {
        if (AntiCheat.INSTANCE.interactRotation.getValue() && mc.player != null) {
            if (event.isPre()) {
                snapAt(mc.player.getYaw(), mc.player.getPitch());
            } else {
                snapBack();
            }
        }
    }

    @EventListener
    public void doAttack(DoAttackEvent event) {
        if (AntiCheat.INSTANCE.interactRotation.getValue() && mc.player != null) {
            if (event.isPre()) {
                snapAt(mc.player.getYaw(), mc.player.getPitch());
            } else {
                snapBack();
            }
        }
    }

    public void snapBack() {
        if (AntiCheat.INSTANCE.snapBackEnum.is(SnapBack.Force)) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), rotationYaw, rotationPitch, mc.player.isOnGround()));
        } else if (AntiCheat.INSTANCE.snapBackEnum.is(SnapBack.Tick))
            snapBack = true;
    }

    public void lookAt(Vec3d directionVec) {
        rotationTo(directionVec);
        snapAt(directionVec);
    }

    public void lookAt(BlockPos pos, Direction side) {
        final Vec3d hitVec = pos.toCenterPos().add(new Vec3d(side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5));
        lookAt(hitVec);
    }

    public void snapAt(float yaw, float pitch) {
        setRenderRotation(yaw, pitch, true);
        if (AntiCheat.INSTANCE.grimRotation.getValue()) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), yaw, pitch, mc.player.isOnGround()));
        } else {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround()));
        }
    }

    public void snapAt(Vec3d directionVec) {
        float[] angle = getRotation(directionVec);
        snapAt(angle[0], angle[1]);
    }

    public void rotationTo(Vec3d vec3d) {
        ROTATE_TIMER.reset();
        directionVec = vec3d;
    }

    public boolean inFov(Vec3d directionVec, float fov) {
        float[] angle = getRotation(crossHairUpdatePos != null ? crossHairUpdatePos : new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ()), directionVec);
        return inFov(angle[0], angle[1], fov);
    }

    public boolean inFov(float yaw, float pitch, float fov) {
        float yawDifferent = MathHelper.angleBetween(yaw, rotationYaw);
        float pitchDifferent = Math.abs(pitch - rotationPitch);
        return yawDifferent * yawDifferent + pitchDifferent * pitchDifferent <= fov * fov;
    }

    @EventListener
    public void onTickMovement(TickMovementEvent event) {
        if (mc.player == null) return;
        crossHairUpdatePos = new Vec3d(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
    }

    @EventListener
    public void update(SendMovementPacketsEvent event) {
        if (AntiCheat.INSTANCE.movementSync() && !BaritoneUtil.isActive()) {
            event.setYaw(nextYaw);
            event.setPitch(nextPitch);
        } else {
            UpdateRotateEvent updateRotateEvent = UpdateRotateEvent.get(event.getYaw(), event.getPitch());
            Alien.EVENT_BUS.post(updateRotateEvent);
            event.setYaw(updateRotateEvent.getYaw());
            event.setPitch(updateRotateEvent.getPitch());
        }
    }

    @EventListener(priority = 999)
    public void update(TickMovementEvent event) {
        if (mc.player == null) return;
        if (AntiCheat.INSTANCE.movementSync() && !BaritoneUtil.isActive()) {
            UpdateRotateEvent updateRotateEvent = UpdateRotateEvent.get(mc.player.getYaw(), mc.player.getPitch());
            Alien.EVENT_BUS.post(updateRotateEvent);
            nextYaw = updateRotateEvent.getYaw();
            nextPitch = updateRotateEvent.getPitch();
            fixYaw = nextYaw;
            fixPitch = nextPitch;
        }
    }

    @EventListener(priority = EventPriority.LOWEST)
    public void onLastRotation(UpdateRotateEvent event) {
        RotationEvent rotationEvent = RotationEvent.get();
        Alien.EVENT_BUS.post(rotationEvent);
        if (rotationEvent.getRotation()) {
            float[] newAngle = injectStep(new float[]{rotationEvent.getYaw(), rotationEvent.getPitch()}, rotationEvent.getSpeed());
            event.setYaw(newAngle[0]);
            event.setPitch(newAngle[1]);
        } else if (rotationEvent.getTarget() != null) {
            float[] newAngle = injectStep(rotationEvent.getTarget(), rotationEvent.getSpeed());
            event.setYaw(newAngle[0]);
            event.setPitch(newAngle[1]);
        } else if (!event.isModified() && AntiCheat.INSTANCE.look.getValue()) {
            if (directionVec != null && !ROTATE_TIMER.passed((long) (AntiCheat.INSTANCE.rotateTime.getValue() * 1000))) {
                float[] newAngle = injectStep(directionVec, AntiCheat.INSTANCE.steps.getValueFloat());
                event.setYaw(newAngle[0]);
                event.setPitch(newAngle[1]);
            }
        }
    }

    public static float fixYaw;
    public static float fixPitch;

    private float prevYaw;
    private float prevPitch;

    @EventListener
    public void travel(TravelEvent e) {
        if (!AntiCheat.INSTANCE.movementSync()) return;
        if (BaritoneUtil.isActive()) return;
        if (mc.player.isRiding())
            return;

        if (e.isPre()) {
            prevYaw = mc.player.getYaw();
            prevPitch = mc.player.getPitch();
            mc.player.setYaw(fixYaw);
            mc.player.setPitch(fixPitch);
        } else {
            mc.player.setYaw(prevYaw);
            mc.player.setPitch(prevPitch);
        }
    }

    @EventListener
    public void onJump(JumpEvent e) {
        if (!AntiCheat.INSTANCE.movementSync()) return;
        if (BaritoneUtil.isActive()) return;
        if (mc.player.isRiding())
            return;

        if (e.isPre()) {
            prevYaw = mc.player.getYaw();
            prevPitch = mc.player.getPitch();
            mc.player.setYaw(fixYaw);
            mc.player.setPitch(fixPitch);
        } else {
            mc.player.setYaw(prevYaw);
            mc.player.setPitch(prevPitch);
        }
    }

    @EventListener
    public void onFirework(FireworkShooterRotationEvent event) {
        if (!AntiCheat.INSTANCE.movementSync()) return;
        if (BaritoneUtil.isActive()) return;
        if (event.shooter == mc.player) {
            event.yaw = fixYaw;
            event.pitch = fixPitch;
            event.cancel();
        }
    }

    @EventListener(priority = -999)
    public void onKeyInput(KeyboardInputEvent e) {
        if (!AntiCheat.INSTANCE.movementSync()) return;
        if (BaritoneUtil.isActive()) return;
        if (HoleSnap.INSTANCE.isOn()) return;
        if (mc.player.isRiding() || Freecam.INSTANCE.isOn())
            return;

        float mF = mc.player.input.movementForward;
        float mS = mc.player.input.movementSideways;
        float delta = (mc.player.getYaw() - fixYaw) * MathHelper.RADIANS_PER_DEGREE;
        float cos = MathHelper.cos(delta);
        float sin = MathHelper.sin(delta);
        mc.player.input.movementSideways = Math.round(mS * cos - mF * sin);
        mc.player.input.movementForward = Math.round(mF * cos + mS * sin);
    }

    public float[] injectStep(Vec3d vec, float steps) {
        float currentYaw = AntiCheat.INSTANCE.serverSide.getValue() ? getLastYaw() : rotationYaw;
        float currentPitch = AntiCheat.INSTANCE.serverSide.getValue() ? getLastPitch() : rotationPitch;

        float yawDelta = MathHelper.wrapDegrees((float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z - mc.player.getZ(), (vec.x - mc.player.getX()))) - 90) - currentYaw);
        float pitchDelta = ((float) (-Math.toDegrees(Math.atan2(vec.y - (mc.player.getPos().y + mc.player.getEyeHeight(mc.player.getPose())), Math.sqrt(Math.pow((vec.x - mc.player.getX()), 2) + Math.pow(vec.z - mc.player.getZ(), 2))))) - currentPitch);

        if (AntiCheat.INSTANCE.random.getValue()) {
            float angleToRad = (float) Math.toRadians(27 * (mc.player.age % 30));
            yawDelta = (float) (yawDelta + Math.sin(angleToRad) * 3) + MathUtil.random(-1, 1);
            pitchDelta = pitchDelta + MathUtil.random(-0.6f, 0.6f);
        }

        if (yawDelta > 180)
            yawDelta = yawDelta - 180;

        float yawStepVal = 180 * steps;

        float clampedYawDelta = MathHelper.clamp(MathHelper.abs(yawDelta), -yawStepVal, yawStepVal);
        float clampedPitchDelta = MathHelper.clamp(pitchDelta, -45, 45);

        float newYaw = currentYaw + (yawDelta > 0 ? clampedYawDelta : -clampedYawDelta);
        float newPitch = MathHelper.clamp(currentPitch + clampedPitchDelta, -90.0F, 90.0F);

        return new float[]{newYaw, newPitch};
    }

    public float[] injectStep(float[] angle, float steps) {
        float currentYaw = AntiCheat.INSTANCE.serverSide.getValue() ? getLastYaw() : rotationYaw;
        float currentPitch = AntiCheat.INSTANCE.serverSide.getValue() ? getLastPitch() : rotationPitch;

        float yawDelta = MathHelper.wrapDegrees(angle[0] - currentYaw);
        float pitchDelta = (angle[1] - currentPitch);

        if (AntiCheat.INSTANCE.random.getValue()) {
            float angleToRad = (float) Math.toRadians(27 * (mc.player.age % 30));
            yawDelta = (float) (yawDelta + Math.sin(angleToRad) * 3) + MathUtil.random(-1, 1);
            pitchDelta = pitchDelta + MathUtil.random(-0.6f, 0.6f);
        }

        if (yawDelta > 180)
            yawDelta = yawDelta - 180;

        float yawStepVal = 180 * steps;
        float pitchStepVal = 90 * steps;

        float clampedYawDelta = MathHelper.clamp(MathHelper.abs(yawDelta), -yawStepVal, yawStepVal);
        float clampedPitchDelta = MathHelper.clamp(pitchDelta, -pitchStepVal, pitchStepVal);

        float newYaw = currentYaw + (yawDelta > 0 ? clampedYawDelta : -clampedYawDelta);
        float newPitch = MathHelper.clamp(currentPitch + clampedPitchDelta, -90.0F, 90.0F);

        return new float[]{newYaw, newPitch};
    }

    @EventListener(priority = -999)
    public void onPacketSend(PacketEvent.Sent event) {
        if (mc.player == null) return;
        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (packet.changesLook()) {
                setLastYaw(packet.getYaw(getLastYaw()));
                setLastPitch(packet.getPitch(getLastPitch()));
                setRenderRotation(getLastYaw(), getLastPitch(), ClientSetting.INSTANCE.sync.getValue());
            }
        }
    }

    @EventListener
    public void onUpdateWalkingPost(TickEvent event) {
        if (event.isPost())
            setRenderRotation(getLastYaw(), getLastPitch(), false);
    }

    public void setRenderRotation(float yaw, float pitch, boolean force) {
        if (mc.player == null) return;
        if (mc.player.age == ticksExisted && !force) {
            return;
        }

        ticksExisted = mc.player.age;
        prevRenderPitch = renderPitch;

        prevRenderYawOffset = renderYawOffset;
        renderYawOffset = getRenderYawOffset(yaw, prevRenderYawOffset);

        prevRotationYawHead = rotationYawHead;
        rotationYawHead = yaw;

        renderPitch = pitch;
    }

    private float getRenderYawOffset(float yaw, float offsetIn) {
        float result = offsetIn;
        float offset;

        double xDif = mc.player.getX() - mc.player.prevX;
        double zDif = mc.player.getZ() - mc.player.prevZ;

        if (xDif * xDif + zDif * zDif > 0.0025000002f) {
            offset = (float) MathHelper.atan2(zDif, xDif) * 57.295776f - 90.0f;
            float wrap = MathHelper.abs(MathHelper.wrapDegrees(yaw) - offset);
            if (95.0F < wrap && wrap < 265.0F) {
                result = offset - 180.0F;
            } else {
                result = offset;
            }
        }

        if (mc.player.handSwingProgress > 0.0F) {
            result = yaw;
        }

        result = offsetIn + MathHelper.wrapDegrees(result - offsetIn) * 0.3f;
        offset = MathHelper.wrapDegrees(yaw - result);

        if (offset < -75.0f) {
            offset = -75.0f;
        } else if (offset >= 75.0f) {
            offset = 75.0f;
        }

        result = yaw - offset;
        if (offset * offset > 2500.0f) {
            result += offset * 0.2f;
        }

        return result;
    }

    public static float[] getRotation(Vec3d eyesPos, Vec3d vec) {
        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch)};
    }

    public static float[] getRotation(Vec3d vec) {
        Vec3d eyesPos = mc.player.getEyePos();
        return getRotation(eyesPos, vec);
    }

    public static float getRenderPitch() {
        return renderPitch;
    }

    public static float getRotationYawHead() {
        return rotationYawHead;
    }

    public static float getRenderYawOffset() {
        return renderYawOffset;
    }

    public static float getPrevRenderPitch() {
        return prevRenderPitch;
    }

    public static float getPrevRotationYawHead() {
        return prevRotationYawHead;
    }

    public static float getPrevRenderYawOffset() {
        return prevRenderYawOffset;
    }

    public float getLastYaw() {
        return lastYaw;
    }

    public void setLastYaw(float lastYaw) {
        this.lastYaw = lastYaw;
        if (AntiCheat.INSTANCE.forceSync.getValue() && Alien.SERVER.playerNull.passedS(0.15)) {
            ((IClientPlayerEntity) mc.player).setLastYaw(lastYaw);
        }
    }

    public float getLastPitch() {
        return lastPitch;
    }

    public void setLastPitch(float lastPitch) {
        this.lastPitch = lastPitch;
        if (AntiCheat.INSTANCE.forceSync.getValue() && Alien.SERVER.playerNull.passedS(0.15)) {
            ((IClientPlayerEntity) mc.player).setLastPitch(lastPitch);
        }
    }
}
