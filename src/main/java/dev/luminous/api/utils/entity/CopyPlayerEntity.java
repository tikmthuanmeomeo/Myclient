package dev.luminous.api.utils.entity;

import com.mojang.authlib.GameProfile;
import dev.luminous.api.utils.math.PredictUtil;
import dev.luminous.asm.accessors.ILivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;

import static dev.luminous.api.utils.Wrapper.mc;

public class CopyPlayerEntity extends PlayerEntity {

    private final boolean onGround;

    public CopyPlayerEntity(PlayerEntity player) {
        this(player, false, 0, 0, 0, false, false, false, false);
    }

    public CopyPlayerEntity(PlayerEntity player, boolean effect) {
        super(mc.world, player.getBlockPos(), player.getYaw(), new GameProfile(player.getGameProfile().getId(), player.getGameProfile().getName()));
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
        setVelocity(player.getVelocity());
        getInventory().clone(player.getInventory());

        if (effect) {
            for (StatusEffectInstance se : new ArrayList<>(player.getStatusEffects())) {
                addStatusEffect(se);
            }
        }
        setAbsorptionAmountUnclamped(player.getAbsorptionAmount());
        setHealth(player.getHealth());
        setBoundingBox(player.getBoundingBox());
    }

    public CopyPlayerEntity(PlayerEntity player, boolean effect, double maxMotionY, int ticks, int simulation, boolean step, boolean doubleStep, boolean jump, boolean inBlockPause) {
        this(player, effect);
        if (ticks > 0) {
            setPosition(PredictUtil.getPos(player, maxMotionY, ticks, simulation, step, doubleStep, jump, inBlockPause));
        }
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
