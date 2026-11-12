package dev.luminous.mod.modules.impl.misc;

import com.mojang.authlib.GameProfile;
import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.DamageUtils;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.ILivingEntity;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.AutoAnchor;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.mod.modules.impl.combat.Criticals;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FakePlayer extends Module {
    public static FakePlayer INSTANCE;
    public static FakePlayerEntity fakePlayer;
    final StringSetting name = add(new StringSetting("Name", "FakePlayer"));
    private final BooleanSetting damage = add(new BooleanSetting("Damage", true));
    private final BooleanSetting autoTotem = add(new BooleanSetting("AutoTotem", true));
    public final BooleanSetting record = add(new BooleanSetting("Record", false));
    public final BooleanSetting play = add(new BooleanSetting("Play", false));

    public FakePlayer() {
        super("FakePlayer", Category.Misc);
        setChinese("假人");
        INSTANCE = this;
    }

    final List<PlayerState> positions = new ArrayList<>();
    int movementTick;
    boolean lastRecordValue = false;

    @Override
    public String getInfo() {
        return name.getValue();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        fakePlayer = new FakePlayerEntity(mc.player, name.getValue());
        mc.world.addEntity(fakePlayer);
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (fakePlayer == null || fakePlayer.clientWorld != mc.world) {
            disable();
            return;
        }
        if (autoTotem.getValue()) {
            if (fakePlayer.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                fakePlayer.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            if (fakePlayer.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                fakePlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }
        if (record.getValue() != lastRecordValue) {
            if (record.getValue()) {
                positions.clear();
            }
        }
        lastRecordValue = record.getValue();
        if (record.getValue()) {
            positions.add(new PlayerState(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch()));
        }
        if (play.getValue() && !positions.isEmpty()) {
            movementTick++;

            if (movementTick >= positions.size()) {
                movementTick = 0;
            }
            PlayerState p = positions.get(movementTick);
            fakePlayer.setYaw(p.yaw);
            fakePlayer.setPitch(p.pitch);
            fakePlayer.setHeadYaw(p.yaw);

            fakePlayer.updateTrackedPosition(p.x, p.y, p.z);
            fakePlayer.updateTrackedPositionAndAngles(p.x, p.y, p.z, p.yaw, p.pitch, 3);
        }
    }

    @Override
    public void onDisable() {
        if (fakePlayer == null) return;
        fakePlayer.kill();
        fakePlayer.setRemoved(Entity.RemovalReason.KILLED);
        fakePlayer.onRemoved();
        fakePlayer = null;
    }

    @EventListener
    public void onAttack(PacketEvent.Send event) {
        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
            if (Criticals.getInteractType(packet) == PlayerInteractEntityC2SPacket.InteractType.ATTACK && Criticals.getEntity(packet) == fakePlayer) {
                mc.world.playSound(mc.player, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 1f);
                float damage = DamageUtils.getAttackDamage(mc.player, fakePlayer);
                if ((mc.player.fallDistance > 0f || Criticals.INSTANCE.isOn() && !Criticals.INSTANCE.mode.is(Criticals.Mode.Ground) && (mc.player.isOnGround() || !Criticals.INSTANCE.onlyGround.getValue()))
                        && (!mc.player.isOnGround() || Criticals.INSTANCE.isOn() && !Criticals.INSTANCE.mode.is(Criticals.Mode.Ground))
                        && !mc.player.isClimbing()
                        && !mc.player.isTouchingWater()
                        && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                        && !mc.player.hasVehicle()) {
                    mc.world.playSound(mc.player, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1f, 1f);
                    mc.player.addCritParticles(fakePlayer);
                }
                if (fakePlayer.hurtTime <= 0) {
                    fakePlayer.onDamaged(mc.world.getDamageSources().generic());
                    if (fakePlayer.getAbsorptionAmount() >= damage) {
                        fakePlayer.setAbsorptionAmount(fakePlayer.getAbsorptionAmount() - damage);
                    } else {
                        float damage2 = damage - fakePlayer.getAbsorptionAmount();
                        fakePlayer.setAbsorptionAmount(0);
                        fakePlayer.setHealth(fakePlayer.getHealth() - damage2);
                    }
                    if (fakePlayer.isDead()) {
                        Alien.POP.onTotemPop(fakePlayer);
                        if (fakePlayer.tryUseTotem(mc.world.getDamageSources().generic())) {
                            fakePlayer.setHealth(10f);
                            new EntityStatusS2CPacket(fakePlayer, EntityStatuses.USE_TOTEM_OF_UNDYING).apply(mc.getNetworkHandler());
                        }
                    }
                }
            }
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (damage.getValue() && fakePlayer != null && fakePlayer.hurtTime <= 0) {
            if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
                if (Math.sqrt(new Vec3d(explosion.getX(), explosion.getY(), explosion.getZ()).squaredDistanceTo(fakePlayer.getPos())) > 10)
                    return;
                float damage;
                if (BlockUtil.getBlock(new BlockPosX(explosion.getX(), explosion.getY(), explosion.getZ())) == Blocks.RESPAWN_ANCHOR) {
                    damage = (float) AutoAnchor.INSTANCE.getAnchorDamage(new BlockPosX(explosion.getX(), explosion.getY(), explosion.getZ()), fakePlayer, fakePlayer);
                } else {
                    damage = AutoCrystal.INSTANCE.calculateDamage(new Vec3d(explosion.getX(), explosion.getY(), explosion.getZ()), fakePlayer, fakePlayer);
                }
                fakePlayer.onDamaged(mc.world.getDamageSources().generic());
                if (fakePlayer.getAbsorptionAmount() >= damage) {
                    fakePlayer.setAbsorptionAmount(fakePlayer.getAbsorptionAmount() - damage);
                } else {
                    float damage2 = damage - fakePlayer.getAbsorptionAmount();
                    fakePlayer.setAbsorptionAmount(0);
                    fakePlayer.setHealth(fakePlayer.getHealth() - damage2);
                }
                if (fakePlayer.isDead()) {
                    Alien.POP.onTotemPop(fakePlayer);
                    if (fakePlayer.tryUseTotem(mc.world.getDamageSources().generic())) {
                        fakePlayer.setHealth(10f);
                        new EntityStatusS2CPacket(fakePlayer, EntityStatuses.USE_TOTEM_OF_UNDYING).apply(mc.getNetworkHandler());
                    }
                }
            }
        }
    }

    public static class FakePlayerEntity extends OtherClientPlayerEntity {

        private final boolean onGround;

        public FakePlayerEntity(PlayerEntity player, String name) {
            super(mc.world, new GameProfile(UUID.fromString("66666666-6666-6666-6666-666666666666"), name));
            copyPositionAndRotation(player);
            prevX = player.prevX;
            prevZ = player.prevZ;
            prevY = player.prevY;

            bodyYaw = player.bodyYaw;
            headYaw = player.headYaw;

            handSwingProgress = player.handSwingProgress;
            handSwingTicks = player.handSwingTicks;
            limbAnimator.setSpeed(player.limbAnimator.getSpeed());
            limbAnimator.pos = player.limbAnimator.getPos();

            ((ILivingEntity) this).setLeaningPitch(((ILivingEntity) player).getLeaningPitch());
            ((ILivingEntity) this).setLastLeaningPitch(((ILivingEntity) player).getLeaningPitch());
            touchingWater = player.isTouchingWater();
            setSneaking(player.isSneaking());
            setPose(player.getPose());
            setFlag(Entity.FALL_FLYING_FLAG_INDEX, player.isFallFlying());
            onGround = (player.isOnGround());
            setOnGround(onGround);

            getInventory().clone(player.getInventory());

            setAbsorptionAmountUnclamped(player.getAbsorptionAmount());
            setHealth(player.getHealth());
            setBoundingBox(player.getBoundingBox());
        }

        @Override
        public boolean isOnGround() {
            return onGround;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }

    private record PlayerState(double x, double y, double z, float yaw, float pitch) {
    }
}