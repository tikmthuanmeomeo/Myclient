package abcxyz.module.combat;

import abcxyz.compat.Color;
import abcxyz.compat.Render3DEvent;
import abcxyz.compat.ShapeMode;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.ModeSetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.core.Rotations;
import abcxyz.core.TickRate;
import abcxyz.crystalaura.Rotation;
import abcxyz.crystalaura.ServerRotation;
import abcxyz.mixin.ClientPlayerEntityAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kill aura: hits the entities around you for you, in the way you choose (1.9 cooldown or 1.8 CPS), with crit logic,
 * shield breaking and four ways of turning to the target (Grim, Track, Snap, None). Your own camera is never turned: the
 * server is told where you look with silent rotation.
 * <p>
 * Based on the Aura of V-Core (MIT License, (c) 2026 ngminhthuan), see THIRD_PARTY_NOTICES/VCore-LICENSE.txt.
 */
public class Aura extends Module {
    private final NumSetting range = add(new NumSetting("Range", 3.1, 1.0, 6.0, 0.1));
    private final NumSetting aimRange = add(new NumSetting("AimRange", 2.0, 0.0, 6.0, 0.1));
    private final NumSetting fov = add(new NumSetting("FOV", 180, 1, 180, 1));
    private final BoolSetting walls = add(new BoolSetting("IgnoreWalls", true));
    private final BoolSetting onlyWeapon = add(new BoolSetting("OnlyWeapon", false));

    private final ModeSetting timing = add(new ModeSetting("Timing", "1.9", "1.9", "1.8"));
    private final NumSetting minCps = add(new NumSetting("MinCPS", 12, 1, 20, 1));
    private final NumSetting maxCps = add(new NumSetting("MaxCPS", 16, 1, 20, 1));
    private final BoolSetting onlyCrit = add(new BoolSetting("OnlyCrit", false));
    private final BoolSetting smartCrit = add(new BoolSetting("SmartCrit", true)).with(onlyCrit);
    private final BoolSetting shieldBreaker = add(new BoolSetting("ShieldBreaker", true));
    private final BoolSetting unpressShield = add(new BoolSetting("UnpressShield", true));
    private final BoolSetting pauseEating = add(new BoolSetting("PauseWhileEating", false));
    private final BoolSetting randomDelay = add(new BoolSetting("RandomHitDelay", false));
    private final BoolSetting tpsSync = add(new BoolSetting("TPSSync", false));
    private final ModeSetting sprint = add(new ModeSetting("Sprint", "HVH", "HVH", "Off"));

    private final ModeSetting rotation = add(new ModeSetting("Rotation", "Grim", "Grim", "Track", "Snap", "None"));
    private final NumSetting minYawStep = add(new NumSetting("MinYawStep", 65, 1, 180, 1));
    private final NumSetting maxYawStep = add(new NumSetting("MaxYawStep", 75, 1, 180, 1));
    private final NumSetting maxPitchStep = add(new NumSetting("MaxPitchStep", 8, 1, 90, 1));
    private final BoolSetting rayTrace = add(new BoolSetting("RayTrace", true));
    private final BoolSetting lockTarget = add(new BoolSetting("LockTarget", true));
    private final ModeSetting sort = add(new ModeSetting("Sort", "Distance", "Distance", "Armor", "Health", "FOV"));

    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting mobs = add(new BoolSetting("Mobs", false));
    private final BoolSetting animals = add(new BoolSetting("Animals", false));
    private final BoolSetting villagers = add(new BoolSetting("Villagers", false));
    private final BoolSetting slimes = add(new BoolSetting("Slimes", false));
    private final BoolSetting hostiles = add(new BoolSetting("Hostiles", false));
    private final BoolSetting ignoreInvisible = add(new BoolSetting("IgnoreInvisible", false));
    private final BoolSetting ignoreNamed = add(new BoolSetting("IgnoreNamed", false));
    private final BoolSetting ignoreTeam = add(new BoolSetting("IgnoreTeam", false));
    private final BoolSetting ignoreCreative = add(new BoolSetting("IgnoreCreative", false));
    private final BoolSetting ignoreNaked = add(new BoolSetting("IgnoreNaked", false));
    private final BoolSetting attackShielding = add(new BoolSetting("AttackShieldingEntities", false));
    private final BoolSetting targets = add(new BoolSetting("Targets", true))
            .with(players, mobs, animals, villagers, slimes, hostiles, ignoreInvisible, ignoreNamed, ignoreTeam, ignoreCreative, ignoreNaked, attackShielding);
    private final BoolSetting rotationGroup = add(new BoolSetting("RotationSettings", true))
            .with(rotation, minYawStep, maxYawStep, maxPitchStep, rayTrace, lockTarget);
    private final BoolSetting attackGroup = add(new BoolSetting("AttackSettings", true))
            .with(timing, minCps, maxCps, smartCrit, shieldBreaker, unpressShield, pauseEating, randomDelay, tpsSync);

    /** The entity being attacked right now (null when there is none). */
    public Entity target;
    private long lastAttack = 0L;
    private long nextGap = 0L;
    private boolean wasEating = false;

    public Aura() {
        super("Aura", "Attacks entities around you automatically", Category.Combat);
        range.label = "Range";
        aimRange.label = "Aim range";
        walls.label = "Ignore walls";
        onlyWeapon.label = "Only weapon";
        minCps.label = "Min CPS (1.8)";
        maxCps.label = "Max CPS (1.8)";
        onlyCrit.label = "Only crit";
        smartCrit.label = "Smart crit";
        shieldBreaker.label = "Shield breaker";
        unpressShield.label = "Unpress shield";
        pauseEating.label = "Pause while eating";
        randomDelay.label = "Random hit delay";
        tpsSync.label = "TPS sync";
        minYawStep.label = "Min yaw step";
        maxYawStep.label = "Max yaw step";
        maxPitchStep.label = "Max pitch step";
        rayTrace.label = "Ray trace";
        lockTarget.label = "Lock target";
        ignoreInvisible.label = "Ignore invisible";
        ignoreNamed.label = "Ignore named";
        ignoreTeam.label = "Ignore team";
        ignoreCreative.label = "Ignore creative";
        ignoreNaked.label = "Ignore naked";
        attackShielding.label = "Attack shielding";
        targets.label = "Targets";
        rotationGroup.label = "Rotation settings";
        attackGroup.label = "Attack settings";
    }

    @Override
    public String info() {
        return target == null ? "" : target.getName().getString();
    }

    @Override
    public void onEnable() {
        target = null;
    }

    @Override
    public void onDisable() {
        target = null;
    }

    // ------------------------------------------------------------------ helpers

    private boolean isTrackLike() {
        return rotation.is("Track") || rotation.is("Grim");
    }

    private double attackRange() {
        return range.d();
    }

    private double acquireRange() {
        return range.d() + (isTrackLike() ? aimRange.d() : 0.0);
    }

    private static double distanceToBox(Vec3d eye, Entity entity) {
        Box box = entity.getBoundingBox();
        Vec3d closest = new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ));
        return eye.distanceTo(closest);
    }

    private boolean hasWeapon() {
        if (!onlyWeapon.on()) return true;
        Item item = mc.player.getMainHandStack().getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem;
    }

    private boolean isConsuming() {
        if (!mc.player.isUsingItem()) return false;
        UseAction action = mc.player.getActiveItem().getUseAction();
        return action == UseAction.EAT || action == UseAction.DRINK;
    }

    private boolean inFov(Entity entity) {
        double dx = entity.getX() - mc.player.getX();
        double dz = entity.getZ() - mc.player.getZ();
        float delta = MathHelper.wrapDegrees((float) (MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0) - MathHelper.wrapDegrees(mc.player.getYaw())));
        return Math.abs(delta) <= fov.i();
    }

    private boolean skip(Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity == mc.player) return true;
        if (!living.isAlive() || living.isDead() || entity instanceof ArmorStandEntity) return true;
        if (entity instanceof SlimeEntity && !slimes.on()) return true;
        if (entity instanceof HostileEntity && !hostiles.on()) return true;
        if (entity instanceof PlayerEntity && !players.on()) return true;
        if (entity instanceof VillagerEntity && !villagers.on()) return true;
        if (entity instanceof MobEntity && !(entity instanceof HostileEntity) && !(entity instanceof AnimalEntity) && !(entity instanceof VillagerEntity) && !mobs.on()) return true;
        if (entity instanceof AnimalEntity && !animals.on()) return true;
        if (!inFov(entity)) return true;
        if (entity instanceof PlayerEntity player) {
            if (player.isCreative() && ignoreCreative.on()) return true;
            if (player.getArmor() == 0 && ignoreNaked.on()) return true;
            if (player.isInvisible() && ignoreInvisible.on()) return true;
            if (ignoreTeam.on() && player.getTeamColorValue() == mc.player.getTeamColorValue() && mc.player.getScoreboardTeam() != null) return true;
        }
        if (entity.hasCustomName() && ignoreNamed.on() && !(entity instanceof PlayerEntity)) return true;
        return distanceToBox(mc.player.getEyePos(), entity) > acquireRange();
    }

    private float fovAngle(LivingEntity e) {
        double dx = e.getX() - mc.player.getX();
        double dz = e.getZ() - mc.player.getZ();
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return Math.abs(yaw - MathHelper.wrapDegrees(mc.player.getYaw()));
    }

    private Entity findTarget() {
        Comparator<LivingEntity> order = switch (sort.get()) {
            case "Armor" -> Comparator.comparingDouble(e -> {
                float v = 0.0f;
                for (ItemStack armor : e.getArmorItems()) {
                    if (armor == null || armor.isEmpty() || armor.getMaxDamage() <= 0) continue;
                    v += (float) (armor.getMaxDamage() - armor.getDamage()) / (float) armor.getMaxDamage();
                }
                return v;
            });
            case "Health" -> Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount());
            case "FOV" -> Comparator.comparingDouble(this::fovAngle);
            default -> Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e));
        };
        LivingEntity best = null;
        for (Entity entity : mc.world.getEntities()) {
            if (skip(entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (best == null || order.compare(living, best) < 0) best = living;
        }
        return best;
    }

    private void updateTarget() {
        Entity candidate = findTarget();
        if (target == null || !lockTarget.on() || sort.is("FOV")) {
            target = candidate;
            return;
        }
        if (skip(target)) target = candidate;
    }

    private Vec3d aimPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        return new Vec3d(
                box.minX + (box.maxX - box.minX) * (0.5 + ThreadLocalRandom.current().nextDouble(-0.1, 0.1)),
                box.minY + (box.maxY - box.minY) * (0.55 + ThreadLocalRandom.current().nextDouble(-0.1, 0.1)),
                box.minZ + (box.maxZ - box.minZ) * (0.5 + ThreadLocalRandom.current().nextDouble(-0.1, 0.1)));
    }

    private static float[] anglesTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(dy, dist)));
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0f, 90.0f)};
    }

    /** The smallest turn the game's mouse can make (so the angles look like they came from a real mouse). */
    private float gcd() {
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double value = sensitivity * 0.6 + 0.2;
        return (float) (Math.pow(value, 1.5) * 0.8 * 0.15);
    }

    private boolean hits(Entity entity, float yaw, float pitch) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(attackRange()));
        Optional<Vec3d> hit = entity.getBoundingBox().expand(entity.getTargetingMargin()).raycast(eye, end);
        if (hit.isEmpty()) return false;
        if (walls.on()) return true;
        HitResult block = mc.world.raycast(new RaycastContext(eye, hit.get(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, (Entity) mc.player));
        return block.getType() == HitResult.Type.MISS;
    }

    private boolean canCrit() {
        PlayerEntity p = mc.player;
        return p.fallDistance > 0.0f && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
    }

    private boolean serverSprint() {
        return ((ClientPlayerEntityAccessor) mc.player).abcxyz$wasSprinting();
    }

    private boolean timingReady(long now) {
        if (timing.is("1.8")) {
            if (nextGap == 0L) nextGap = gap18();
            return now - lastAttack >= nextGap;
        }
        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        float needed = 0.9f;
        if (tpsSync.on()) {
            float tps = TickRate.tps();
            if (tps > 0.0f && tps < 20.0f) needed = Math.min(1.0f, needed + (20.0f - tps) / 40.0f);
        }
        if (cooldown < needed) return false;
        if (randomDelay.on() && now - lastAttack < nextGap) return false;
        return critAllows();
    }

    private boolean critAllows() {
        if (!smartCrit.on() || mc.player.getAbilities().flying || mc.player.isGliding()) return true;
        if (mc.player.isInLava() || mc.player.isSubmergedInWater()) return true;
        if (canCrit()) return true;
        if (onlyCrit.on()) return false;
        // not a crit: only hit right away when you are not trying to jump
        if (mc.options.jumpKey.isPressed() && !mc.player.isOnGround()) return false;
        return mc.player.isOnGround();
    }

    private long gap18() {
        int lo = Math.min(minCps.i(), maxCps.i());
        int hi = Math.max(minCps.i(), maxCps.i());
        double cps = lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
        return (long) (1000.0 / Math.max(1.0, cps));
    }

    // ------------------------------------------------------------------ main loop

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            target = null;
            return;
        }
        if (!mc.player.isAlive() || mc.player.isSpectator() || !hasWeapon()) {
            target = null;
            return;
        }
        boolean eating = pauseEating.on() && isConsuming();
        if (eating) {
            wasEating = true;
            return;
        }
        if (wasEating) {
            wasEating = false;
            lastAttack = 0L;
        }
        updateTarget();
        if (target == null) return;

        long now = System.currentTimeMillis();
        Vec3d eye = mc.player.getEyePos();
        double reachNow = distanceToBox(eye, target);
        float[] want = anglesTo(eye, aimPoint(target));

        if (rotation.is("Track")) {
            Rotation base = ServerRotation.sent() != null ? ServerRotation.sent() : new Rotation(mc.player.getYaw(), mc.player.getPitch());
            float yawDiff = MathHelper.wrapDegrees(want[0] - base.yaw());
            float pitchDiff = want[1] - base.pitch();
            float yawStep = Math.min(Math.abs(yawDiff), (float) (minYawStep.d() + ThreadLocalRandom.current().nextDouble() * Math.max(0.0, maxYawStep.d() - minYawStep.d())));
            float pitchStep = Math.min(Math.abs(pitchDiff), (float) maxPitchStep.d());
            float g = gcd();
            float yaw = base.yaw() + Math.copySign(yawStep, yawDiff);
            float pitch = MathHelper.clamp(base.pitch() + Math.copySign(pitchStep, pitchDiff), -90.0f, 90.0f);
            ServerRotation.request(new Rotation(yaw - yaw % g, pitch - pitch % g));
        }

        if (reachNow > attackRange()) return;
        if (!timingReady(now)) return;

        float yaw;
        float pitch;
        boolean silent = false;
        if (rotation.is("Track")) {
            Rotation sent = ServerRotation.sent() != null ? ServerRotation.sent() : new Rotation(mc.player.getYaw(), mc.player.getPitch());
            yaw = sent.yaw();
            pitch = sent.pitch();
        } else if (rotation.is("None")) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
        } else {
            float g = gcd();
            float jitterYaw = (float) ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
            float jitterPitch = (float) ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
            yaw = want[0] + jitterYaw;
            pitch = MathHelper.clamp(want[1] + jitterPitch, -90.0f, 90.0f);
            yaw -= yaw % g;
            pitch -= pitch % g;
            silent = true;
        }
        if (rayTrace.on() && !hits(target, yaw, pitch)) return;
        if (target instanceof PlayerEntity victim && victim.isBlocking() && !attackShielding.on() && !shieldBreaker.on()) return;

        attack(yaw, pitch, silent);
        lastAttack = now;
        nextGap = timing.is("1.8") ? gap18() : (randomDelay.on() ? ThreadLocalRandom.current().nextLong(0L, 100L) : 0L);
    }

    private void attack(float yaw, float pitch, boolean silent) {
        boolean blocking = mc.player.isUsingItem() && mc.player.getActiveItem().getUseAction() == UseAction.BLOCK;
        if (blocking && timing.is("1.9") && unpressShield.on() && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, 0));
        }
        boolean stoppedSprint = false;
        if (timing.is("1.9") && sprint.is("HVH") && serverSprint() && mc.getNetworkHandler() != null) {
            mc.player.setSprinting(false);
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            stoppedSprint = true;
        }
        if (silent) Rotations.silentLookAngles(yaw, pitch);
        if (!breakShield()) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        if (silent) Rotations.restore();
        if (stoppedSprint && mc.getNetworkHandler() != null) {
            mc.player.setSprinting(true);
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
        if (blocking && timing.is("1.9") && unpressShield.on()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
        }
    }

    /** The target holds a shield up: hit it with an axe from the hotbar instead. */
    private boolean breakShield() {
        if (!shieldBreaker.on() || !(target instanceof PlayerEntity victim) || !victim.isBlocking() || mc.getNetworkHandler() == null) return false;
        int axe = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getStack(slot).getItem() instanceof AxeItem) {
                axe = slot;
                break;
            }
        }
        if (axe == -1) return false;
        int held = mc.player.getInventory().selectedSlot;
        if (axe != held) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axe));
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (axe != held) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(held));
        return true;
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        if (target == null || mc.world == null) return;
        Box b = target.getBoundingBox();
        Render3DEvent event = new Render3DEvent(context);
        event.renderer.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, new Color(255, 60, 60, 40), new Color(255, 60, 60, 220), ShapeMode.Both, 0);
        event.flush();
    }
}
