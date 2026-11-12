package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.events.impl.MovedEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.path.BaritoneUtil;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

public class Speed extends Module {

    public static Speed INSTANCE;
    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Strafe));
    public final SliderSetting collideSpeed = add(new SliderSetting("CollideSpeed", 0.08, 0, 0.08, 0.01, () -> mode.is(Mode.Grim)));
    private final BooleanSetting strict =
            add(new BooleanSetting("Strict", true, () -> mode.is(Mode.Grim)));
    private final BooleanSetting boat =
            add(new BooleanSetting("BoatLongJump", true, () -> mode.is(Mode.Grim)));
    public final SliderSetting boatExpand = add(new SliderSetting("BoatExpand", 0.2, 0, 1, 0.01, () -> mode.is(Mode.Grim)));
    public final SliderSetting boatSpeed = add(new SliderSetting("BoatSpeed", 0.2, -2, 2, 0.01, () -> mode.is(Mode.Grim)));
    public final SliderSetting boatJump = add(new SliderSetting("BoatJump", 0.2, 0, 2, 0.01, () -> mode.is(Mode.Grim)));

    private final BooleanSetting inWater =
            add(new BooleanSetting("InWater", false, () -> !mode.is(Mode.Grim)));
    private final BooleanSetting inBlock =
            add(new BooleanSetting("InBlock", false, () -> !mode.is(Mode.Grim)));
    private final BooleanSetting airStop =
            add(new BooleanSetting("AirStop", false, () -> !mode.is(Mode.Grim)));
    private final SliderSetting lagTime =
            add(new SliderSetting("LagTime", 500, 0, 1000, 1, () -> !mode.is(Mode.Grim)));

    private final BooleanSetting jump =
            add(new BooleanSetting("Jump", true, () -> mode.is(Mode.Strafe)));
    private final SliderSetting strafeSpeed =
            add(new SliderSetting("Speed", 0.2873, 0, 1.0, 0.0001, () -> mode.is(Mode.Strafe)));
    private final BooleanSetting explosions =
            add(new BooleanSetting("ExplosionsBoost", false, () -> mode.is(Mode.Strafe)));
    private final BooleanSetting velocity =
            add(new BooleanSetting("VelocityBoost", true, () -> mode.is(Mode.Strafe)));
    private final SliderSetting multiplier =
            add(new SliderSetting("H-Factor", 1.0f, 0.0f, 5.0f, 0.01, () -> mode.is(Mode.Strafe)));
    private final SliderSetting vertical =
            add(new SliderSetting("V-Factor", 1.0f, 0.0f, 5.0f, 0.01, () -> mode.is(Mode.Strafe)));
    private final SliderSetting coolDown =
            add(new SliderSetting("CoolDown", 1000, 0, 5000, 1, () -> mode.is(Mode.Strafe)));
    private final BooleanSetting slow =
            add(new BooleanSetting("Slowness", false, () -> mode.is(Mode.Strafe)));
    private final Timer expTimer = new Timer();
    private final Timer lagTimer = new Timer();
    private boolean stop;
    private double speed;
    private double distance;

    private int strictTicks;
    private int strafe = 4;
    private int stage;
    private double lastExp;
    private boolean boost;

    public Speed() {
        super("Speed", Category.Movement);
        setChinese("加速");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            speed = MovementUtil.getSpeed(false);
            distance = MovementUtil.getDistance2D();
        }

        stage = 4;
    }

    @EventListener(priority = EventPriority.HIGH)
    public void invoke(PacketEvent.Receive event) {
        if (BaritoneUtil.isActive()) return;
        if (mode.is(Mode.Strafe)) {
            if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
                if (mc.player != null
                        && packet.getEntityId() == mc.player.getId()
                        && this.velocity.getValue()) {
                    double speed = Math.sqrt(
                            packet.getVelocityX() * packet.getVelocityX()
                                    + packet.getVelocityZ() * packet.getVelocityZ());

                    this.lastExp = this.expTimer
                            .passed(this.coolDown.getValueInt())
                            ? speed
                            : (speed - this.lastExp);

                    if (this.lastExp > 0) {
                        this.expTimer.reset();

                        this.speed +=
                                this.lastExp * this.multiplier.getValue();

                        this.distance +=
                                this.lastExp * this.multiplier.getValue();

                        if (MovementUtil.getMotionY() > 0
                                && this.vertical.getValue() != 0) {
                            MovementUtil.setMotionY(MovementUtil.getMotionY() * this.vertical.getValue());
                        }
                    }
                }
            } else if (event.getPacket() instanceof ExplosionS2CPacket packet) {
                if (this.explosions.getValue()) {
                    if (mc.player.getPos().distanceTo(new Vec3d(packet.getX(), packet.getY(), packet.getZ())) < 15) {
                        double speed = Math.sqrt(packet.getPlayerVelocityX() * packet.getPlayerVelocityX()
                                + packet.getPlayerVelocityZ() * packet.getPlayerVelocityZ());
                        this.lastExp = this.expTimer
                                .passed(this.coolDown.getValueInt())
                                ? speed
                                : (speed - this.lastExp);

                        if (this.lastExp > 0) {
                            this.expTimer.reset();

                            this.speed +=
                                    this.lastExp * this.multiplier.getValue();

                            this.distance +=
                                    this.lastExp * this.multiplier.getValue();

                            if (MovementUtil.getMotionY() > 0) {
                                MovementUtil.setMotionY(MovementUtil.getMotionY() * this.vertical.getValue());
                            }
                        }
                    }
                }
            }
        }
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            lagTimer.reset();
            resetStrafe();
        }
    }

    @EventListener
    public void onMove(MovedEvent event) {
        if (nullCheck()) return;
        double dx = mc.player.getX() - mc.player.prevX;
        double dz = mc.player.getZ() - mc.player.prevZ;
        distance = Math.sqrt(dx * dx + dz * dz);
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (mode.is(Mode.Grim)) {
            if (!MovementUtil.isMoving()) {
                return;
            }

            int collisions = 0;
            Box box = strict.getValue() ? mc.player.getBoundingBox() : mc.player.getBoundingBox().expand(1.0);

            for (Entity entity : Alien.THREAD.getEntities()) {
                Box entityBox = entity.getBoundingBox();
                if (boat.getValue() && mc.player.isOnGround() && entity instanceof BoatEntity && box.intersects(entityBox.expand(boatExpand.getValue()))) {
                    double yaw = Math.toRadians(Sprint.getSprintYaw(mc.player.getYaw()));
                    double boost = boatSpeed.getValue();
                    mc.player.setVelocity(-Math.sin(yaw) * boost, boatJump.getValue(), Math.cos(yaw) * boost);
                    return;
                } else if (box.intersects(entityBox) && canCauseSpeed(entity)) {
                    collisions++;
                }
            }

            double yaw = Math.toRadians(Sprint.getSprintYaw(mc.player.getYaw()));
            double boost = this.collideSpeed.getValue() * collisions;
            mc.player.addVelocity(-Math.sin(yaw) * boost, 0.0, Math.cos(yaw) * boost);
        }
    }

    private boolean canCauseSpeed(Entity entity) {
        return entity != mc.player && entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity);
    }

    @EventListener
    public void invoke(MoveEvent event) {
        if (!MovementUtil.isMoving() && airStop.getValue() && !mode.is(Mode.Grim)) {
            MovementUtil.setMotionX(0);
            MovementUtil.setMotionZ(0);
        }
        if (!this.inWater.getValue() && (mc.player.isSubmergedInWater() || mc.player.isTouchingWater() || mc.player.isInLava())
                || mc.player.isRiding()
                || mc.player.isHoldingOntoLadder()
                || !inBlock.getValue() && EntityUtil.isInsideBlock()
                || mc.player.getAbilities().flying
                || mc.player.isFallFlying()
                || !MovementUtil.isMoving()) {
            resetStrafe();
            this.stop = true;
            return;
        }
        if (mode.is(Mode.Strafe)) {

            if (this.stop) {
                this.stop = false;
                return;
            }

            if (!lagTimer.passed(this.lagTime.getValueInt())) {
                return;
            }

            if (this.stage == 1) {
                this.speed = 1.35 * MovementUtil.getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()) - 0.01;
            } else if (this.stage == 2 && mc.player.isOnGround() && (mc.options.jumpKey.isPressed() || this.jump.getValue())) {
                double yMotion = 0.3999 + MovementUtil.getJumpSpeed();
                MovementUtil.setMotionY(yMotion);
                event.setY(yMotion);
                this.speed = this.speed * (this.boost ? 1.6835 : 1.395);
            } else if (this.stage == 3) {
                this.speed = this.distance - 0.66
                        * (this.distance - MovementUtil.getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()));

                this.boost = !this.boost;
            } else {
                if ((BlockUtil.canCollide(null,
                        mc.player
                                .getBoundingBox()
                                .offset(0.0, MovementUtil.getMotionY(), 0.0))
                        || mc.player.collidedSoftly)
                        && this.stage > 0) {
                    this.stage = 1;
                }

                this.speed = this.distance - this.distance / 159.0;
            }

            this.speed = Math.min(this.speed, 10);
            this.speed = Math.max(this.speed, MovementUtil.getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()));
            double n = mc.player.input.movementForward;
            double n2 = mc.player.input.movementSideways;
            double n3 = mc.player.getYaw();
            if (n == 0.0 && n2 == 0.0) {
                event.setX(0.0);
                event.setZ(0.0);
            } else if (n != 0.0 && n2 != 0.0) {
                n *= Math.sin(0.7853981633974483);
                n2 *= Math.cos(0.7853981633974483);
            }
            event.setX((n * this.speed * -Math.sin(Math.toRadians(n3)) + n2 * this.speed * Math.cos(Math.toRadians(n3))) * 0.99);
            event.setZ((n * this.speed * Math.cos(Math.toRadians(n3)) - n2 * this.speed * -Math.sin(Math.toRadians(n3))) * 0.99);

            this.stage++;
            return;
        }
        double speedEffect = 1.0;
        double slowEffect = 1.0;
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            double amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
            speedEffect = 1 + (0.2 * (amplifier + 1));
        }
        if (mc.player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            double amplifier = mc.player.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier();
            slowEffect = 1 + (0.2 * (amplifier + 1));
        }
        final double base = 0.2873f * speedEffect / slowEffect;
        float jumpEffect = 0.0f;
        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            jumpEffect += (mc.player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1f;
        }

        if (mode.getValue() == Mode.StrafeStrict) {
            if (!lagTimer.passed(lagTime.getValueInt())) {
                return;
            }
            if (strafe == 1) {
                speed = 1.35f * base - 0.01f;
            } else if (strafe == 2) {
                if (mc.player.input.jumping || !mc.player.isOnGround()) {
                    return;
                }
                float jump = 0.3999999463558197f + jumpEffect;
                event.setY(jump);
                MovementUtil.setMotionY(jump);
                speed *= 2.149;
            } else if (strafe == 3) {
                double moveSpeed = 0.66 * (distance - base);
                speed = distance - moveSpeed;
            } else {
                if ((!mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().offset(0,
                        mc.player.getVelocity().getY(), 0)) || mc.player.verticalCollision) && strafe > 0) {
                    strafe = 1;
                }
                speed = distance - distance / 159.0;
            }
            strictTicks++;
            speed = Math.max(speed, base);
            double baseMax = 0.465 * speedEffect / slowEffect;
            double baseMin = 0.44 * speedEffect / slowEffect;
            speed = Math.min(speed, strictTicks > 25 ? baseMax : baseMin);
            if (strictTicks > 50) {
                strictTicks = 0;
            }
            final Vec2f motion = handleStrafeMotion((float) speed);
            event.setX(motion.x);
            event.setZ(motion.y);
            strafe++;
        }
    }

    public Vec2f handleStrafeMotion(final float speed) {
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        float yaw = mc.player.prevYaw + (mc.player.getYaw() - mc.player.prevYaw) * mc.getRenderTickCounter().getTickDelta(true);
        if (forward == 0.0f && strafe == 0.0f) {
            return Vec2f.ZERO;
        } else if (forward != 0.0f) {
            if (strafe >= 1.0f) {
                yaw += forward > 0.0f ? -45 : 45;
                strafe = 0.0f;
            } else if (strafe <= -1.0f) {
                yaw += forward > 0.0f ? 45 : -45;
                strafe = 0.0f;
            }
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }
        float rx = (float) Math.cos(Math.toRadians(yaw));
        float rz = (float) -Math.sin(Math.toRadians(yaw));
        return new Vec2f((forward * speed * rz) + (strafe * speed * rx),
                (forward * speed * rx) - (strafe * speed * rz));
    }

    public void resetStrafe() {
        strafe = 4;
        strictTicks = 0;
        speed = 0.0f;
        distance = 0.0;
    }

    public enum Mode {
        Strafe,
        StrafeStrict,
        Grim,
    }
}