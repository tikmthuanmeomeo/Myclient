package dev.luminous.asm.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.Alien;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.api.utils.world.InteractUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.player.Freecam;
import dev.luminous.mod.modules.impl.player.InteractTweaks;
import dev.luminous.mod.modules.impl.render.*;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Shadow
    @Final
    MinecraftClient client;
    @Shadow
    private float fovMultiplier;
    @Shadow
    private float lastFovMultiplier;
    @Shadow
    private boolean renderingPanorama;
    @Shadow
    private float zoom;
    @Shadow
    private float zoomX;
    @Shadow
    private float zoomY;
    @Shadow
    private float viewDistance;
    @Unique
    private Entity cameraEntity;
    @Unique
    private float originalYaw;
    @Unique
    private float originalPitch;

    @Shadow
    private static HitResult ensureTargetInRange(HitResult hitResult, Vec3d cameraPos, double interactionRange) {
        Vec3d vec3d = hitResult.getPos();
        if (!vec3d.isInRange(cameraPos, interactionRange)) {
            Vec3d vec3d2 = hitResult.getPos();
            Direction direction = Direction.getFacing(vec3d2.x - cameraPos.x, vec3d2.y - cameraPos.y, vec3d2.z - cameraPos.z);
            return BlockHitResult.createMissed(vec3d2, direction, BlockPos.ofFloored(vec3d2));
        } else {
            return hitResult;
        }
    }

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void onShowFloatingItem(ItemStack floatingItem, CallbackInfo info) {
        if (floatingItem.getItem() == Items.TOTEM_OF_UNDYING && NoRender.INSTANCE.isOn() && NoRender.INSTANCE.totem.getValue()) {
            info.cancel();
        }
    }

    @Redirect(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F"), require = 0)
    private float applyCameraTransformationsMathHelperLerpProxy(float delta, float first, float second) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.nausea.getValue()) return 0;
        return MathHelper.lerp(delta, first, second);
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void tiltViewWhenHurtHook(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.hurtCam.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    public void hookOutline(CallbackInfoReturnable<Boolean> cir) {
        if (HighLight.INSTANCE.isOn()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z", opcode = Opcodes.GETFIELD, ordinal = 0), method = "renderWorld")
    void render3dHook(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Module.nullCheck()) {
            return;
        }
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();

        MatrixStack matrixStack = new MatrixStack();
        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));

        TextUtil.lastProjMat.set(RenderSystem.getProjectionMatrix());
        TextUtil.lastModMat.set(RenderSystem.getModelViewMatrix());
        TextUtil.lastWorldSpaceMatrix.set(matrixStack.peek().getPositionMatrix());

        Alien.FPS.record();
        Alien.MODULE.render3D(matrixStack);
    }

    @Inject(at = @At(value = "TAIL"), method = "renderWorld")
    void render3dTail(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Chams.INSTANCE.isOn() && Chams.INSTANCE.hand.booleanValue) RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("HEAD"), cancellable = true)
    public void getFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> ci) {
        if (!this.renderingPanorama && (Fov.INSTANCE.isOn() || Zoom.on)) {
            double d = 70.0;
            if (changingFov) {
                if (Fov.INSTANCE.isOn()) {
                    double fov = Fov.INSTANCE.fov.getValue();

                    if (Zoom.on) {
                        ci.setReturnValue(Math.min(Math.max(fov - Zoom.INSTANCE.currentFov, 1), 177));
                    } else {
                        ci.setReturnValue(fov);
                    }
                    return;
                }
                d = this.client.options.getFov().getValue();
                d *= MathHelper.lerp(tickDelta, this.lastFovMultiplier, this.fovMultiplier);
                if (Zoom.on) {
                    d = (Math.min(Math.max(d - Zoom.INSTANCE.currentFov, 1), 177));
                }
            } else {
                if (Fov.INSTANCE.isOn()) {
                    ci.setReturnValue(Fov.INSTANCE.itemFov.getValue());
                    return;
                }
            }

            if (camera.getFocusedEntity() instanceof LivingEntity && ((LivingEntity) camera.getFocusedEntity()).isDead()) {
                float f = Math.min((float) ((LivingEntity) camera.getFocusedEntity()).deathTime + tickDelta, 20.0F);
                d /= (1.0F - 500.0F / (f + 500.0F)) * 2.0F + 1.0F;
            }

            CameraSubmersionType cameraSubmersionType = camera.getSubmersionType();
            if (cameraSubmersionType == CameraSubmersionType.LAVA || cameraSubmersionType == CameraSubmersionType.WATER) {
                d *= MathHelper.lerp(this.client.options.getFovEffectScale().getValue(), 1.0, 0.85714287F);
            }

            ci.setReturnValue(d);
        }
    }

    @Inject(method = "getNightVisionStrength", at = @At("HEAD"), cancellable = true)
    private static void getNightVisionStrengthHook(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> cir) {
        StatusEffectInstance statusEffectInstance = entity.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (statusEffectInstance == null)
            cir.setReturnValue(1f);
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    public void postRender3dHook(RenderTickCounter tickCounter, CallbackInfo ci) {
        Alien.SHADER.renderShaders();
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("TAIL"), cancellable = true)
    public void getBasicProjectionMatrixHook(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (AspectRatio.INSTANCE.isOn()) {
            MatrixStack matrixStack = new MatrixStack();
            matrixStack.peek().getPositionMatrix().identity();
            if (zoom != 1.0f) {
                matrixStack.translate(zoomX, -zoomY, 0.0f);
                matrixStack.scale(zoom, zoom, 1.0f);
            }
            matrixStack.peek().getPositionMatrix().mul(new Matrix4f().setPerspective((float) (fov * 0.01745329238474369), AspectRatio.INSTANCE.ratio.getValueFloat(), 0.05f, viewDistance * 4.0f));
            cir.setReturnValue(matrixStack.peek().getPositionMatrix());
        }
    }

    @Inject(method = "findCrosshairTarget", at = @At("HEAD"), cancellable = true)
    private void findCrosshairTarget(Entity camera, double blockInteractionRange, double entityInteractionRange, float tickDelta, CallbackInfoReturnable<HitResult> cir) {
        if (Freecam.INSTANCE.isOn()) {
            cir.setReturnValue(InteractUtil.getRtxTarget(Freecam.INSTANCE.getFakeYaw(), Freecam.INSTANCE.getFakePitch(), Freecam.INSTANCE.getFakeX(), Freecam.INSTANCE.getFakeY(), Freecam.INSTANCE.getFakeZ()));
            return;
        }

        double d = Math.max(blockInteractionRange, entityInteractionRange);
        double e = MathHelper.square(d);
        Vec3d vec3d = camera.getCameraPosVec(tickDelta);
        InteractTweaks.INSTANCE.isActive = InteractTweaks.INSTANCE.ghostHand();
        HitResult hitResult = camera.raycast(d, tickDelta, false);
        InteractTweaks.INSTANCE.isActive = false;
        double f = hitResult.getPos().squaredDistanceTo(vec3d);
        if (hitResult.getType() != HitResult.Type.MISS) {
            e = f;
            d = Math.sqrt(f);
        }

        Vec3d vec3d2 = camera.getRotationVec(tickDelta);
        Vec3d vec3d3 = vec3d.add(vec3d2.x * d, vec3d2.y * d, vec3d2.z * d);
        Box box = camera.getBoundingBox().stretch(vec3d2.multiply(d)).expand(1.0, 1.0, 1.0);
        if (!InteractTweaks.INSTANCE.noEntityTrace()) {
            EntityHitResult entityHitResult = ProjectileUtil.raycast(camera, vec3d, vec3d3, box, (entity) -> !entity.isSpectator() && entity.canHit(), e);
            cir.setReturnValue(entityHitResult != null && entityHitResult.getPos().squaredDistanceTo(vec3d) < f ? ensureTargetInRange(entityHitResult, vec3d, entityInteractionRange) : ensureTargetInRange(hitResult, vec3d, blockInteractionRange));
        } else {
            cir.setReturnValue(ensureTargetInRange(hitResult, vec3d, blockInteractionRange));
        }
    }
}
