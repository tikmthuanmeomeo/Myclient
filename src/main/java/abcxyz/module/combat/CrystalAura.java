/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client (no anchor aura, friend list or macro lock).
 */
package abcxyz.module.combat;

import abcxyz.compat.Render3DEvent;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.KeySetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.crystalaura.Combo;
import abcxyz.crystalaura.CrystalDamage;
import abcxyz.crystalaura.Crystals;
import abcxyz.crystalaura.HurtClock;
import abcxyz.crystalaura.LootGuard;
import abcxyz.crystalaura.MeleeAttacks;
import abcxyz.crystalaura.Rotation;
import abcxyz.crystalaura.RotationUtils;
import abcxyz.crystalaura.ServerRotation;
import abcxyz.crystalaura.ShotView;
import abcxyz.crystalaura.TargetingUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.BlockView;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;

public final class CrystalAura extends Module {
    private static final int COUNTER_CAP = 1000;
    private static final int IDLE_TICKS = 10;
    private static final float FACE_PLACE_HEALTH = 10.0f;
    private static final float FACE_PLACE_DAMAGE = 2.0f;
    private static final float ARMOR_LOW = 0.1f;
    private static final float BREAK_FACTOR = 0.6f;
    private static final double AIM_INSET = 0.1;
    private static final int MAX_BREAK_ATTEMPTS = 3;
    private static final double CHAIN_RANGE = 12.0;
    private static final int WEAPON_SETTLE_TICKS = 2;
    private static final float LOOK_BONUS = 2.0f;
    private static final int MAX_LEAD_TICKS = 5;
    private static final float LOOK_MATCH_DEGREES = 2.0f;
    private static final double PLACE_BREAK_MARGIN = 0.1;
    private static final float ONE_LOOK_BONUS = 2.0f;
    private static final float OBSIDIAN_UPGRADE_BAR = 8.0f;
    private static final float OBSIDIAN_MIN_GAIN = 5.0f;
    private static final float OBSIDIAN_MIN_RATIO = 2.0f;
    private static final float EAGER_UPGRADE_BAR = 11.0f;
    private static final float EAGER_MIN_GAIN = 3.0f;
    private static final float EAGER_MIN_RATIO = 1.4f;
    private static final double PLACE_REACH_MARGIN = 0.1;
    private static final float HELD_VIEW_BONUS = 20.0f;
    private final BoolSetting requireHold = this.bool("require-hold", "Require Hold", false);
    private final KeySetting holdKey = this.key("hold-key", "Hold Key", -1);
    private final NumSetting placeDelay = this.num("place-delay", "Place Delay", 0.0, 0.0, 10.0, 1.0);
    private final NumSetting breakDelay = this.num("break-delay", "Break Delay", 0.0, 0.0, 10.0, 1.0);
    private final NumSetting minDamage = this.num("min-damage", "Min Damage", 4.0, 0.0, 20.0, 0.5);
    private final NumSetting maxSelfDamage = this.num("max-self-damage", "Max Self Damage", 8.0, 0.0, 20.0, 0.5);
    private final BoolSetting facePlace = this.bool("face-place", "Face Place", true);
    private final KeySetting forceFacePlace = this.key("force-face-place", "Force Face Place", -1);
    private final BoolSetting autoObsidian = this.bool("auto-obsidian", "Auto Obsidian", true);
    private final BoolSetting switchBack = this.bool("switch-back", "Switch Back", true);
    private final NumSetting turnSpeed = this.num("turn-speed", "Turn Speed", 4.5, 1.0, 10.0, 0.5);
    private final NumSetting viewAngle = this.num("view-angle", "View Angle", 90.0, 30.0, 180.0, 5.0);
    private final BoolSetting humanize = this.bool("humanize", "Human Pace", true);
    private final BoolSetting protectLoot = this.bool("protect-loot", "Protect Loot", true);
    private final BoolSetting render = this.bool("render", "Render", true);
    private final int renderColor = -2728705;
    private final ShotView view = new ShotView();
    private boolean engaged;
    private int idleTicks;
    private int savedSlot = -1;
    private int ownSlot = -1;
    private int ticksSinceBreak = 1000;
    private int ticksSincePlace = 1000;
    private int breakGap;
    private int placeGap;
    private ItemStack lastMain;
    private int mainAge = 1000;
    private int pendingBreakId = -1;
    private int pendingBreakAge = 1000;
    private BlockPos pendingObsidian;
    private int pendingObsidianAge = 1000;
    private final Int2IntOpenHashMap breakAttempts = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap doomed = new Int2IntOpenHashMap();
    private float steerMin;
    private float steerSelf;
    private int steerUntil = -1;
    private float tickMin;
    private float tickSelf;
    private int eagerUntil = -1;
    private int floorY;
    private int floorUntil = -1;
    private int tickFloor = Integer.MIN_VALUE;
    private int fastUntil = -1;
    private boolean fullSpeed;

    public CrystalAura() {
        super("CrystalAura", "Places and breaks end crystals around your targets for the most damage", Category.Combat);
        this.requireHold.with(this.holdKey);
        this.facePlace.with(this.forceFacePlace);
    }

    private static String pascal(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private BoolSetting bool(String id, String label, boolean def) {
        BoolSetting s = add(new BoolSetting(pascal(id), def));
        s.label = label;
        return s;
    }

    private NumSetting num(String id, String label, double def, double min, double max, double step) {
        NumSetting s = add(new NumSetting(pascal(id), def, min, max, step));
        s.label = label;
        return s;
    }

    private KeySetting key(String id, String label, int def) {
        KeySetting s = add(new KeySetting(pascal(id), def));
        s.label = label;
        return s;
    }

    @Override
    public void onEnable() {
        this.resetState();
    }

    @Override
    public void onDisable() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            this.restoreSlot(player);
        }
        this.resetState();
        this.view.clear();
    }

    @Override
    public void onTick() {
        ItemStack offhand;
        boolean offhandUse;
        boolean anchors;
        MinecraftClient mc = MinecraftClient.getInstance();
        this.ageCounters();
        ClientPlayerEntity player = mc.player;
        if (player != null && mc.world != null) {
            LootGuard.update(mc.world, player);
        }
        if (player == null || mc.world == null || mc.interactionManager == null || player.isDead()) {
            this.disengage(mc, null);
            Crystals.forgetRtt();
            return;
        }
        HurtClock.tick(player);
        this.forgetGone(mc.world);
        boolean steered = player.age <= this.steerUntil;
        this.tickMin = steered ? this.steerMin : ((float) this.minDamage.d());
        this.tickSelf = steered ? this.steerSelf : ((float) this.maxSelfDamage.d());
        this.tickFloor = player.age <= this.floorUntil ? this.floorY : Integer.MIN_VALUE;
        this.fullSpeed = player.age <= this.fastUntil;
        ItemStack main = player.getMainHandStack();
        if (main != this.lastMain) {
            this.lastMain = main;
            this.mainAge = 0;
        }
        Look look = ServerRotation.canRotate(player) ? Look.SILENT : Look.CROSSHAIR;
        boolean crystals = !this.requireHold.on() || Crystals.isKeyDown(mc, this.holdKey.key());
        AnchorAuraStub aura = null;
        boolean bl = anchors = aura != null && aura.isEnabled() && look == Look.SILENT;
        if (!MeleeAttacks.canAct(mc) || player.isSpectator() || !crystals && !anchors) {
            this.disengage(mc, player);
            return;
        }
        if (this.ownSlot != -1 && !this.ownsSelection(player)) {
            this.forgetSlots();
        }
        boolean bl2 = offhandUse = !Crystals.isCombatItem(offhand = player.getOffHandStack()) && !offhand.isOf(Items.END_CRYSTAL) && !offhand.isOf(Items.OBSIDIAN);
        if (MeleeAttacks.rightClickComing(mc) && (offhandUse || main.isOf(Items.RESPAWN_ANCHOR) || main.isOf(Items.GLOWSTONE))) {
            this.disengage(mc, player);
            return;
        }
        if (MeleeAttacks.actedThisTick((PlayerEntity)player) || !MeleeAttacks.handsFree(mc, player) || !this.ownsSelection(player) && !CrystalAura.mayTakeHand(player, anchors)) {
            this.idle(mc, player);
            return;
        }
        List<LivingEntity> enemies = Combo.enemies(mc, player);
        if (enemies.isEmpty()) {
            this.disengage(mc, player);
            return;
        }
        Rotation fixedLook = null;
        if (look == Look.CROSSHAIR) {
            fixedLook = RotationUtils.entityRotation((Entity)player);
            if (ServerRotation.pending() || !CrystalAura.sameLook(ServerRotation.sent(), fixedLook)) {
                this.idle(mc, player);
                return;
            }
        }
        int window = Crystals.confirmWindow(mc, player);
        EndCrystalEntity lastHit = this.lastHit(mc.world, window);
        Fight fight = new Fight(player, mc.world, enemies, Combo.friends(mc, player), window, Crystals.rttTicks(mc, player), fixedLook, look == Look.SILENT ? 2.0f : 0.0f, Crystals.isKeyDown(mc, this.forceFacePlace.key()));
        Combo.Shot shot = this.choose(mc, fight, lastHit, crystals, anchors ? aura : null);
        if (shot == null) {
            this.idle(mc, player);
            return;
        }
        this.engage();
        Object object = shot.plan();
        if (object instanceof Plan) {
            Plan plan = (Plan)object;
            if (this.render.on()) {
                this.show(fight, plan);
            }
            if (!this.perform(mc, player, fight, plan, look) || look != Look.SILENT) {
                return;
            }
            Plan following = this.planCrystal(fight, this.lastHit(mc.world, window), 1, this.stagedBreak(fight));
            Rotation sent = ServerRotation.sent();
            this.turn(following == null || fight.holds(following) ? sent : fight.lookAt(following.aim()));
        } else {
            aura.show(shot);
            if (this.fire(mc, player, fight, aura, shot)) {
                ServerRotation.request(ServerRotation.sent());
            }
        }
    }

    public void steer(ClientPlayerEntity player, float minDamage, float maxSelf) {
        this.steerMin = minDamage;
        this.steerSelf = maxSelf;
        this.steerUntil = player.age + 2;
    }

    public void buildEagerly(ClientPlayerEntity player) {
        this.eagerUntil = player.age + 2;
    }

    public void crystalsFrom(ClientPlayerEntity player, int lowest) {
        this.floorY = lowest;
        this.floorUntil = player.age + 2;
    }

    public void fullSpeed(ClientPlayerEntity player) {
        this.fastUntil = player.age + 2;
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        if (this.render.on()) {
            Render3DEvent event = new Render3DEvent(context);
            this.view.draw(event);
            event.flush();
            this.view.label(context);
        }
    }

    /** Packets that matter to the crystal aura (called from ClientPlayNetworkHandlerMixin on the main thread). */
    public void onPacket(Packet<?> incoming) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        Packet<?> class_25962 = incoming;
        if (class_25962 instanceof BundleS2CPacket) {
            BundleS2CPacket bundle = (BundleS2CPacket)class_25962;
            for (Packet packet : bundle.getPackets()) {
                this.note(player, packet);
            }
        } else {
            this.note(player, incoming);
        }
        HurtClock.note(player, incoming);
    }

    private void note(ClientPlayerEntity player, Packet<?> packet) {
        EntitySpawnS2CPacket spawn;
        if (packet instanceof EntitySpawnS2CPacket && (spawn = (EntitySpawnS2CPacket)packet).getEntityType() == EntityType.END_CRYSTAL) {
            Crystals.noteSpawn(player, BlockPos.ofFloored((double)spawn.getX(), (double)(spawn.getY() - 1.0), (double)spawn.getZ()));
        }
    }

    private static boolean mayTakeHand(ClientPlayerEntity player, boolean anchors) {
        ItemStack main = player.getMainHandStack();
        return main.isOf(Items.END_CRYSTAL) || main.isOf(Items.OBSIDIAN) || Crystals.isCombatItem(main) || anchors && (main.isOf(Items.RESPAWN_ANCHOR) || main.isOf(Items.GLOWSTONE));
    }

    private boolean ownsSelection(ClientPlayerEntity player) {
        return this.ownSlot != -1 && player.getInventory().selectedSlot == this.ownSlot;
    }

    private void engage() {
        this.engaged = true;
        this.idleTicks = 0;
    }

    private void idle(MinecraftClient mc, ClientPlayerEntity player) {
        if (!this.engaged || ++this.idleTicks >= 10) {
            this.disengage(mc, player);
        }
    }

    private void disengage(MinecraftClient mc, ClientPlayerEntity player) {
        this.engaged = false;
        this.idleTicks = 0;
        if (player == null) {
            this.forgetSlots();
        } else if (!(mc.currentScreen != null || MeleeAttacks.actedThisTick((PlayerEntity)player))) {
            this.restoreSlot(player);
        }
    }

    private void restoreSlot(ClientPlayerEntity player) {
        if (this.ownSlot == -1) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        if (this.switchBack.on() && PlayerInventory.isValidHotbarIndex((int)this.savedSlot) && this.savedSlot != this.ownSlot && inventory.selectedSlot == this.ownSlot) {
            inventory.selectedSlot = this.savedSlot;
        }
        this.forgetSlots();
    }

    private void forgetSlots() {
        this.savedSlot = -1;
        this.ownSlot = -1;
    }

    private void resetState() {
        this.engaged = false;
        this.idleTicks = 0;
        this.forgetSlots();
        this.ticksSinceBreak = 1000;
        this.ticksSincePlace = 1000;
        this.breakGap = 0;
        this.placeGap = 0;
        this.lastMain = null;
        this.mainAge = 1000;
        this.pendingBreakId = -1;
        this.pendingBreakAge = 1000;
        this.pendingObsidian = null;
        this.pendingObsidianAge = 1000;
        this.breakAttempts.clear();
        this.doomed.clear();
        Crystals.forgetRtt();
    }

    private void forgetGone(ClientWorld level) {
        CrystalAura.forgetGone(level, this.breakAttempts.keySet());
        CrystalAura.forgetGone(level, this.doomed.keySet());
    }

    private static void forgetGone(ClientWorld level, IntSet ids) {
        IntIterator iterator = ids.iterator();
        while (iterator.hasNext()) {
            if (level.getEntityById(iterator.nextInt()) != null) continue;
            iterator.remove();
        }
    }

    private static boolean oneLook(Vec3d eye, Box crystal, Vec3d placeAim, double reach) {
        Vec3d direction = placeAim.subtract(eye).normalize();
        return crystal.raycast(eye, eye.add(direction.multiply(reach))).isPresent();
    }

    private boolean canHostSoon(Fight fight, BlockPos base, Entity breaking) {
        BlockPos above = base.up();
        if (!fight.level.isAir(above)) {
            return false;
        }
        Box space = new Box((double)above.getX(), (double)above.getY(), (double)above.getZ(), (double)above.getX() + 1.0, (double)above.getY() + 2.0, (double)above.getZ() + 1.0);
        for (Entity entity : fight.level.getOtherEntities(breaking, space)) {
            boolean aside;
            LivingEntity living;
            if (!entity.isAlive()) continue;
            if (!(entity instanceof LivingEntity) || !fight.enemies.contains(living = (LivingEntity)entity)) {
                return false;
            }
            Box then = living.getBoundingBox().offset(Combo.lead(living, fight.arriveLead));
            boolean over = then.minY >= space.maxY;
            boolean bl = aside = then.maxX <= space.minX || then.minX >= space.maxX || then.maxZ <= space.minZ || then.minZ >= space.maxZ;
            if (over || aside) continue;
            return false;
        }
        return true;
    }

    private EndCrystalEntity lastHit(ClientWorld level, int window) {
        EndCrystalEntity crystal;
        if (this.pendingBreakId < 0 || this.pendingBreakAge >= window) {
            return null;
        }
        Entity class_12972 = level.getEntityById(this.pendingBreakId);
        return class_12972 instanceof EndCrystalEntity && (crystal = (EndCrystalEntity)class_12972).isAlive() ? crystal : null;
    }

    private Combo.Shot choose(MinecraftClient mc, Fight fight, EndCrystalEntity lastHit, boolean crystals, AnchorAuraStub aura) {
        Combo.Shot picked;
        ArrayList<Combo.Shot> shots = new ArrayList<Combo.Shot>();
        Combo.Shot aimed = null;
        float rival = 0.0f;
        if (crystals) {
            Plan opening = this.stagedBreak(fight);
            Plan plan = this.planCrystal(fight, lastHit, 0, opening);
            if (plan != null) {
                aimed = this.shot(fight, plan, false);
                if (this.due(plan, 0)) {
                    shots.add(aimed);
                }
            }
            if (aura != null && opening != null) {
                rival = this.shot(fight, opening, true).value();
            }
        }
        if (aura != null) {
            shots.addAll(aura.shots(mc, fight.player, fight.enemies, fight.friends, fight.rtt, rival));
        }
        return (picked = Combo.pick(shots)) != null ? picked : aimed;
    }

    private void show(Fight fight, Plan plan) {
        Side side = fight.side(plan.step());
        boolean baseToCome = plan.step() == Step.OBSIDIAN;
        Vec3d center = plan.step() == Step.BREAK ? plan.crystal().getPos() : Crystals.explosionAt(plan.target());
        float most = 0.0f;
        for (CrystalDamage.Victim enemy : side.enemies) {
            most = Math.max(most, enemy.damage(center, baseToCome));
        }
        BlockPos base = plan.step() == Step.BREAK ? plan.crystal().getBlockPos().down() : plan.target();
        this.view.show(new Box(base), this.renderColor, most, side.self.damage(center, baseToCome));
    }

    private Plan planCrystal(Fight fight, EndCrystalEntity lastHit, int ahead, Plan opening) {
        boolean staged;
        Plan breakPlan = this.planBreak(fight, false);
        if (breakPlan != null && this.due(breakPlan, ahead)) {
            return breakPlan;
        }
        boolean bl = staged = breakPlan != null || opening != null || Crystals.anyPlacing(fight.player, fight.window);
        Plan placePlan = staged ? null : this.planPlacement(fight, lastHit, lastHit == null);
        return placePlan != null ? placePlan : breakPlan;
    }

    private Plan stagedBreak(Fight fight) {
        return this.planBreak(fight, true);
    }

    private Combo.Shot shot(Fight fight, Plan plan, boolean opening) {
        Vec3d center;
        Step step = plan.step();
        Vec3d VanillaChestLootTableGenerator = center = step == Step.BREAK ? plan.crystal().getPos() : Crystals.explosionAt(plan.target());
        int readyIn = step == Step.BREAK ? 0 : (step == Step.CRYSTAL ? fight.rtt : fight.rtt + 1);
        return new Combo.Shot(Combo.Kind.valueOf(step.name()), plan.aim(), plan.route(), this.score(fight, step, center, Float.NEGATIVE_INFINITY, opening), CrystalAura.kills(fight, step, center, opening), readyIn, plan);
    }

    private Plan planBreak(Fight fight, boolean opening) {
        double reach = fight.player.getEntityInteractionRange();
        Plan best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        Box around = fight.player.getBoundingBox().expand(reach + 2.0);
        for (EndCrystalEntity crystal : fight.level.getNonSpectatingEntities(EndCrystalEntity.class, around)) {
            float score;
            Plan plan;
            Vec3d eye;
            if (!crystal.isAlive() || this.breakAttempts.get(crystal.getId()) >= 3 || crystal.getId() == this.pendingBreakId && this.pendingBreakAge < fight.window || this.doomed.containsKey(crystal.getId()) && fight.player.age - this.doomed.get(crystal.getId()) < fight.window || (eye = fight.eye).squaredDistanceTo(TargetingUtils.closestPoint(eye, crystal.getBoundingBox())) > reach * reach || crystal.getY() < (double)this.tickFloor - 0.01) continue;
            Vec3d center = crystal.getPos();
            if (this.protectLoot.on() && LootGuard.near(center) || this.bound(fight, Step.BREAK, center) + fight.lookBonus <= bestScore || (plan = this.breakLook(fight, crystal)) == null || !fight.inView(plan) || (score = this.score(fight, Step.BREAK, center, bestScore - fight.lookBonus, opening)) == Float.NEGATIVE_INFINITY || (score += fight.lookCredit(plan)) <= bestScore) continue;
            best = plan;
            bestScore = score;
        }
        if (best == null) {
            return null;
        }
        int weapon = Crystals.weaponRoute(fight.player);
        return weapon == -1 ? null : best.withRoute(weapon);
    }

    private Plan breakLook(Fight fight, EndCrystalEntity crystal) {
        Plan throughBase;
        if (fight.fixedLook != null) {
            Plan plan = new Plan(Step.BREAK, crystal, null, null, crystal.getBoundingBox().getCenter(), -2);
            return CrystalAura.hitAlong(fight.player, plan, fight.eye, fight.fixedLook) != null ? plan : null;
        }
        BlockPos base = crystal.getBlockPos().down();
        if (Crystals.isBase(fight.level.getBlockState(base)) && CrystalAura.hitAlong(fight.player, throughBase = new Plan(Step.BREAK, crystal, base, base, CrystalAura.aimOnTop(fight.level, base), -2), fight.aimEye, fight.lookAt(throughBase.aim())) != null) {
            return throughBase;
        }
        Vec3d toward = fight.towardLook(crystal.getBoundingBox().contract(0.1));
        Plan turned = new Plan(Step.BREAK, crystal, null, null, toward, -2);
        if (CrystalAura.hitAlong(fight.player, turned, fight.aimEye, fight.lookAt(toward)) != null) {
            return turned;
        }
        Vec3d nearest = TargetingUtils.closestPoint(fight.aimEye, crystal.getBoundingBox().contract(0.1));
        Plan plan = new Plan(Step.BREAK, crystal, null, null, nearest, -2);
        return CrystalAura.hitAlong(fight.player, plan, fight.aimEye, fight.lookAt(nearest)) != null ? plan : null;
    }

    private Plan planPlacement(Fight fight, EndCrystalEntity breaking, boolean mayLayBase) {
        Plan crystal = this.planBase(fight, Step.CRYSTAL, breaking);
        if (!this.autoObsidian.on() || !mayLayBase) {
            return crystal;
        }
        boolean eager = fight.player.age <= this.eagerUntil;
        float crystalDamage = crystal == null ? 0.0f : this.enemyDamage(fight, crystal);
        if (crystal != null) {
            float bar = eager ? 11.0f : 8.0f;
            if (crystalDamage >= bar) {
                return crystal;
            }
        }
        Plan obsidian = this.planBase(fight, Step.OBSIDIAN, null);
        if (obsidian == null || crystal == null) {
            return obsidian == null ? crystal : obsidian;
        }
        float obsidianDamage = this.enemyDamage(fight, obsidian);
        float minGain = eager ? 3.0f : 5.0f;
        float minRatio = eager ? 1.4f : 2.0f;
        boolean upgrade = obsidianDamage >= crystalDamage + minGain && obsidianDamage >= crystalDamage * minRatio;
        return upgrade ? obsidian : crystal;
    }

    private float enemyDamage(Fight fight, Plan plan) {
        Vec3d center = Crystals.explosionAt(plan.target());
        float most = 0.0f;
        for (CrystalDamage.Victim enemy : fight.side((Step)plan.step()).enemies) {
            most = Math.max(most, enemy.damage(center, plan.step() == Step.OBSIDIAN));
        }
        return most;
    }

    private Plan planBase(Fight fight, Step step, EndCrystalEntity breaking) {
        Candidate candidate;
        BlockState anyBase;
        ClientPlayerEntity player = fight.player;
        ClientWorld level = fight.level;
        Item item = step == Step.CRYSTAL ? Items.END_CRYSTAL : Items.OBSIDIAN;
        if (Crystals.route(player, item, anyBase = Blocks.OBSIDIAN.getDefaultState(), this.savedSlot) == -1) {
            return null;
        }
        if (step == Step.OBSIDIAN && Crystals.route(player, Items.END_CRYSTAL, anyBase, this.savedSlot) == -1) {
            return null;
        }
        double placeReach = fight.placeReach;
        double breakable = player.getEntityInteractionRange() - 0.1;
        Vec3d eye = fight.eye;
        BlockPos origin = BlockPos.ofFloored((Position)eye);
        int radius = MathHelper.ceil((double)placeReach);
        float cycle = step == Step.CRYSTAL && fight.lookBonus > 0.0f ? 2.0f : 0.0f;
        ArrayList<Candidate> candidates = new ArrayList<Candidate>();
        BlockPos.Mutable cell = new BlockPos.Mutable();
        BlockPos.Mutable below = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dy = -radius; dy <= radius; ++dy) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    float bound;
                    Box crystal;
                    cell.set((Vec3i)origin, dx, dy, dz);
                    if (cell.getY() + 1 < this.tickFloor) continue;
                    BlockState state = level.getBlockState((BlockPos)cell);
                    if (step == Step.CRYSTAL ? !Crystals.isBase(state) : !state.isReplaceable()) continue;
                    below.set((Vec3i)cell, Direction.DOWN);
                    if (step == Step.OBSIDIAN && !level.getBlockState((BlockPos)below).isSideSolidFullSquare((BlockView)level, (BlockPos)below, Direction.UP) || (step == Step.CRYSTAL ? Crystals.placing(player, (BlockPos)cell, fight.window) : cell.equals((Object)this.pendingObsidian) && this.pendingObsidianAge < fight.window)) continue;
                    BlockPos base = cell.toImmutable();
                    BlockPos clicked = step == Step.CRYSTAL ? base : below.toImmutable();
                    Vec3d aim = CrystalAura.aimOnTop(level, clicked);
                    if (eye.squaredDistanceTo(aim) > placeReach * placeReach || eye.squaredDistanceTo(TargetingUtils.closestPoint(eye, crystal = Crystals.boxOn(base))) > breakable * breakable || this.protectLoot.on() && LootGuard.near(Crystals.explosionAt(base)) || (bound = this.bound(fight, step, Crystals.explosionAt(base))) == Float.NEGATIVE_INFINITY) continue;
                    candidates.add(new Candidate(new Plan(step, null, clicked, base, aim, -2), bound));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::bound).reversed());
        Plan best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        Iterator dz = candidates.iterator();
        while (dz.hasNext() && !((candidate = (Candidate)dz.next()).bound() + fight.lookBonus + cycle <= bestScore)) {
            Plan plan;
            Plan found = candidate.plan();
            float score = this.score(fight, step, Crystals.explosionAt(found.target()), bestScore - fight.lookBonus - cycle, false);
            if (score == Float.NEGATIVE_INFINITY || !level.getWorldBorder().contains(found.clicked()) || !this.canHostSoon(fight, found.target(), (Entity)(step == Step.CRYSTAL ? breaking : null)) || (plan = this.reachable(fight, found)) == null || !fight.inView(plan)) continue;
            score += fight.lookCredit(plan);
            if (cycle > 0.0f && CrystalAura.oneLook(fight.aimEye, Crystals.boxOn(plan.target()), plan.aim(), breakable)) {
                score += cycle;
            }
            if (score <= bestScore) continue;
            best = plan;
            bestScore = score;
        }
        if (best == null) {
            return null;
        }
        int route = Crystals.route(player, item, level.getBlockState(best.clicked()), this.savedSlot);
        return route == -1 ? null : best.withRoute(route);
    }

    private Plan reachable(Fight fight, Plan plan) {
        if (fight.fixedLook == null) {
            BlockPos clicked = plan.clicked();
            double top = plan.aim().y;
            Vec3d near = fight.towardLook(new Box((double)clicked.getX() + 0.1, top, (double)clicked.getZ() + 0.1, (double)clicked.getX() + 1.0 - 0.1, top, (double)clicked.getZ() + 1.0 - 0.1));
            Plan turned = new Plan(plan.step(), null, clicked, plan.target(), near, plan.route());
            if (fight.eye.squaredDistanceTo(near) <= fight.placeReach * fight.placeReach && CrystalAura.hitAlong(fight.player, turned, fight.aimEye, fight.lookAt(near)) != null) {
                return turned;
            }
        }
        if (CrystalAura.hitAlong(fight.player, plan, fight.aimEye, fight.lookAt(plan.aim())) != null) {
            return plan;
        }
        if (plan.step() != Step.CRYSTAL || fight.fixedLook != null) {
            return null;
        }
        Vec3d middle = Vec3d.ofCenter((Vec3i)plan.clicked());
        for (Direction side : Direction.Type.HORIZONTAL) {
            Plan aimed;
            Vec3d face = middle.add((double)side.getOffsetX() * 0.4, 0.0, (double)side.getOffsetZ() * 0.4);
            if ((fight.aimEye.x - face.x) * (double)side.getOffsetX() + (fight.aimEye.z - face.z) * (double)side.getOffsetZ() <= 0.0 || CrystalAura.hitAlong(fight.player, aimed = new Plan(plan.step(), null, plan.clicked(), plan.target(), face, plan.route()), fight.aimEye, fight.lookAt(face)) == null) continue;
            return aimed;
        }
        return null;
    }

    private static Vec3d aimOnTop(ClientWorld level, BlockPos pos) {
        double top = level.getBlockState(pos).getOutlineShape((BlockView)level, pos).getMax(Direction.Axis.Y);
        return new Vec3d((double)pos.getX() + 0.5, (double)pos.getY() + top - 0.1, (double)pos.getZ() + 0.5);
    }

    private float need(LivingEntity target, boolean breaking, boolean forced) {
        float damage = forced || this.facePlace.on() && CrystalAura.facePlaced(target) ? Math.min(2.0f, this.tickMin) : this.tickMin;
        return breaking ? damage * 0.6f : damage;
    }

    private static boolean facePlaced(LivingEntity target) {
        return CrystalDamage.health(target) <= 10.0f || Combo.inHole(target) || CrystalAura.armorLow(target);
    }

    private static boolean armorLow(LivingEntity target) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack;
            if (!slot.isArmorSlot() || !(stack = target.getEquippedStack(slot)).isDamageable() || !((float)(stack.getMaxDamage() - stack.getDamage()) <= (float)stack.getMaxDamage() * 0.1f)) continue;
            return true;
        }
        return false;
    }

    private float bound(Fight fight, Step step, Vec3d center) {
        float best = Float.NEGATIVE_INFINITY;
        for (CrystalDamage.Victim enemy : fight.side((Step)step).enemies) {
            LivingEntity entity = enemy.entity();
            float upper = enemy.upperBound(center);
            if (!Combo.kills(upper, entity)) {
                if (upper < this.need(entity, step == Step.BREAK, fight.forced)) continue;
            }
            best = Math.max(best, Combo.value(upper, entity));
        }
        return best;
    }

    private float score(Fight fight, Step step, Vec3d center, float toBeat, boolean opening) {
        Side side = fight.side(step);
        boolean baseToCome = step == Step.OBSIDIAN;
        float selfDamage = side.self.damage(center, baseToCome);
        if (selfDamage > this.tickSelf || CrystalDamage.endangers((PlayerEntity)fight.player, selfDamage)) {
            return Float.NEGATIVE_INFINITY;
        }
        for (CrystalDamage.Victim friend : side.friends) {
            float damage = friend.damage(center, baseToCome);
            if (!(damage > this.tickSelf) && !CrystalDamage.endangers((PlayerEntity)friend.entity(), damage)) continue;
            return Float.NEGATIVE_INFINITY;
        }
        float best = Float.NEGATIVE_INFINITY;
        for (CrystalDamage.Victim enemy : side.enemies) {
            LivingEntity entity = enemy.entity();
            float effective = CrystalAura.effective(fight, enemy, step, center, opening);
            if (!Combo.kills(effective, entity)) {
                if (effective < this.need(entity, step == Step.BREAK, fight.forced)) continue;
            }
            best = Math.max(best, Combo.value(effective, entity) - 0.5f * selfDamage);
        }
        return best > toBeat ? best : Float.NEGATIVE_INFINITY;
    }

    private static boolean kills(Fight fight, Step step, Vec3d center, boolean opening) {
        for (CrystalDamage.Victim enemy : fight.side((Step)step).enemies) {
            if (!Combo.kills(CrystalAura.effective(fight, enemy, step, center, opening), enemy.entity())) continue;
            return true;
        }
        return false;
    }

    private static float effective(Fight fight, CrystalDamage.Victim enemy, Step step, Vec3d center, boolean opening) {
        int ahead = step == Step.BREAK ? (opening ? HurtClock.opensIn(enemy.entity(), fight.rtt) : 0) : fight.rtt;
        return HurtClock.effective(enemy, enemy.raw(center, step == Step.OBSIDIAN), ahead, fight.rtt);
    }

    private static HitResult hitAlong(ClientPlayerEntity player, Plan plan, Vec3d eye, Rotation look) {
        if (look == null) {
            return null;
        }
        Vec3d direction = Vec3d.fromPolar((float)look.pitch(), (float)look.yaw());
        if (plan.step() == Step.BREAK) {
            Box box = plan.crystal().getBoundingBox();
            if (box.contains(eye)) {
                return new EntityHitResult((Entity)plan.crystal(), eye);
            }
            Vec3d at = box.raycast(eye, eye.add(direction.multiply(player.getEntityInteractionRange()))).orElse(null);
            return at != null && MeleeAttacks.lineIsClear(player, eye, at) ? new EntityHitResult((Entity)plan.crystal(), at) : null;
        }
        ClientWorld level = (ClientWorld)player.getWorld();
        BlockState clicked = level.getBlockState(plan.clicked());
        BlockHitResult hit = clicked.getOutlineShape((BlockView)level, plan.clicked()).raycast(eye, eye.add(direction.multiply(player.getBlockInteractionRange())), plan.clicked());
        if (hit == null || !MeleeAttacks.lineIsClear(player, eye, hit.getPos())) {
            return null;
        }
        if (plan.step() == Step.OBSIDIAN && !plan.target().equals((Object)Crystals.placedObsidian(level, player, hit, clicked))) {
            return null;
        }
        return hit;
    }

    private boolean perform(MinecraftClient mc, ClientPlayerEntity player, Fight fight, Plan plan, Look look) {
        boolean acted;
        HitResult hit;
        Vec3d eye = player.getEyePos();
        Rotation sent = ServerRotation.sent();
        Rotation next = look == Look.SILENT ? sent : RotationUtils.entityRotation((Entity)player);
        HitResult ItemStackParticleEffect = hit = sent == null ? null : CrystalAura.hitAlong(player, plan, eye, sent);
        if (hit != null && next != sent && CrystalAura.hitAlong(player, plan, eye, next) == null) {
            hit = null;
        }
        if (plan.route() != -2) {
            this.select(player, plan.route());
        }
        boolean bl = hit != null && this.due(plan, 0) && (plan.step() == Step.BREAK ? this.strike(mc, player, fight, plan.crystal()) : this.place(mc, player, plan, (BlockHitResult)hit)) ? true : (acted = false);
        if (acted) {
            MeleeAttacks.claimTick(mc);
        } else if (look == Look.SILENT) {
            this.turn(hit != null ? sent : fight.lookAt(plan.aim()));
        }
        return acted;
    }

    private boolean fire(MinecraftClient mc, ClientPlayerEntity player, Fight fight, AnchorAuraStub aura, Combo.Shot shot) {
        if (shot.route() != -2) {
            this.select(player, shot.route());
        }
        if (!aura.ready(mc, player, shot)) {
            this.turn(fight.lookAt(shot.aim()));
            return false;
        }
        aura.fire(mc, player, shot);
        return true;
    }

    private boolean strike(MinecraftClient mc, ClientPlayerEntity player, Fight fight, EndCrystalEntity crystal) {
        if (this.mainAge < 2 && !Crystals.enchantsAddDamage(player.getMainHandStack()) && !Crystals.dealsDamage((LivingEntity)player, ItemStack.EMPTY)) {
            return false;
        }
        if (!Crystals.strike(mc, player, (Entity)crystal)) {
            return false;
        }
        this.markBreak(crystal);
        this.breakAttempts.addTo(crystal.getId(), 1);
        this.ticksSinceBreak = 0;
        this.breakGap = this.humanize.on() && !this.fullSpeed ? Crystals.humanGap() : 0;
        for (CrystalDamage.Victim enemy : fight.side((Step)Step.BREAK).enemies) {
            float raw = enemy.raw(crystal.getPos(), false);
            if (!(raw > 0.0f)) continue;
            HurtClock.shot(player, enemy.entity(), raw, crystal.getId(), fight.rtt);
        }
        Box blast = crystal.getBoundingBox().expand(12.0);
        for (EndCrystalEntity other : fight.level.getNonSpectatingEntities(EndCrystalEntity.class, blast)) {
            if (other == crystal || !(other.squaredDistanceTo((Entity)crystal) <= 144.0)) continue;
            this.doomed.put(other.getId(), player.age);
        }
        return true;
    }

    private boolean place(MinecraftClient mc, ClientPlayerEntity player, Plan plan, BlockHitResult hit) {
        if (!Crystals.rightClick(mc, player, hit)) {
            return false;
        }
        if (plan.step() == Step.CRYSTAL) {
            Crystals.notePlace(player, plan.target());
        } else {
            this.pendingObsidian = plan.target();
            this.pendingObsidianAge = 0;
        }
        this.ticksSincePlace = 0;
        this.placeGap = this.humanize.on() && !this.fullSpeed ? Crystals.humanGap() : 0;
        return true;
    }

    private void turn(Rotation look) {
        ServerRotation.request(look, this.fullSpeed ? 10.0f : ((float) this.turnSpeed.d()));
    }

    private static boolean sameLook(Rotation a, Rotation b) {
        return a != null && Math.abs(RotationUtils.yawDelta(a.yaw(), b.yaw())) <= 2.0f && Math.abs(a.pitch() - b.pitch()) <= 2.0f;
    }

    private boolean due(Plan plan, int ahead) {
        return plan.step() == Step.BREAK ? this.ticksSinceBreak + ahead > Math.max(this.breakDelay.i(), this.breakGap - 1) : this.ticksSincePlace + ahead > Math.max(this.placeDelay.i(), this.placeGap - 1);
    }

    private void select(ClientPlayerEntity player, int slot) {
        PlayerInventory inventory = player.getInventory();
        if (this.savedSlot == -1) {
            this.savedSlot = inventory.selectedSlot;
        }
        inventory.selectedSlot = slot;
        this.ownSlot = slot;
        this.lastMain = player.getMainHandStack();
        this.mainAge = 0;
    }

    private void markBreak(EndCrystalEntity crystal) {
        this.pendingBreakId = crystal.getId();
        this.pendingBreakAge = 0;
    }

    private void ageCounters() {
        this.ticksSinceBreak = Math.min(this.ticksSinceBreak + 1, 1000);
        this.ticksSincePlace = Math.min(this.ticksSincePlace + 1, 1000);
        this.mainAge = Math.min(this.mainAge + 1, 1000);
        this.pendingBreakAge = Math.min(this.pendingBreakAge + 1, 1000);
        this.pendingObsidianAge = Math.min(this.pendingObsidianAge + 1, 1000);
    }

        private static enum Look {
        SILENT,
        CROSSHAIR;

    }

        private final class Fight {
        final ClientPlayerEntity player;
        final ClientWorld level;
        final Vec3d eye;
        final Vec3d aimEye;
        final List<LivingEntity> enemies;
        final List<LivingEntity> friends;
        final int window;
        final int rtt;
        final Rotation fixedLook;
        final Rotation serverLook;
        final float lookBonus;
        final int arriveLead;
        private final int placeLead;
        private final int selfPlaceLead;
        final boolean forced;
        final double placeReach;
        private Side breakSide;
        private Side placeSide;

        Fight(ClientPlayerEntity player, ClientWorld level, List<LivingEntity> enemies, List<LivingEntity> friends, int window, int rtt, Rotation fixedLook, float lookBonus, boolean forced) {
            this.player = player;
            this.level = level;
            this.eye = player.getEyePos();
            this.aimEye = fixedLook != null ? this.eye : this.eye.add(player.getVelocity());
            this.enemies = enemies;
            this.friends = friends;
            this.window = window;
            this.rtt = rtt;
            this.fixedLook = fixedLook;
            this.serverLook = ServerRotation.sent();
            this.lookBonus = lookBonus;
            this.arriveLead = Math.min(rtt, 5);
            this.placeLead = Math.min(2 * rtt, 5);
            this.selfPlaceLead = Math.min(rtt, 5);
            this.forced = forced;
            this.placeReach = player.getBlockInteractionRange() - 0.1;
        }

        Side side(Step step) {
            if (step == Step.BREAK) {
                if (this.breakSide == null) {
                    this.breakSide = new Side(this.player, this.enemies, this.friends, this.arriveLead, 0);
                }
                return this.breakSide;
            }
            if (this.placeSide == null) {
                this.placeSide = new Side(this.player, this.enemies, this.friends, this.placeLead, this.selfPlaceLead);
            }
            return this.placeSide;
        }

        Rotation lookAt(Vec3d aim) {
            return this.fixedLook != null ? this.fixedLook : RotationUtils.fromTo(this.aimEye, aim);
        }

        boolean holds(Plan plan) {
            return CrystalAura.hitAlong(this.player, plan, this.eye, this.serverLook) != null;
        }

        float lookCredit(Plan plan) {
            return this.lookBonus > 0.0f && this.holds(plan) ? this.lookBonus : 0.0f;
        }

        boolean inView(Plan plan) {
            if (this.fixedLook != null || CrystalAura.((float) this.viewAngle.d()) >= 180.0f) {
                return true;
            }
            float off = Math.abs(RotationUtils.yawDelta(this.player.getYaw(), RotationUtils.fromTo(this.aimEye, plan.aim()).yaw()));
            return off <= CrystalAura.((float) this.viewAngle.d()) || off <= CrystalAura.((float) this.viewAngle.d()) + 20.0f && this.holds(plan);
        }

        Vec3d towardLook(Box box) {
            double along;
            Rotation from = this.serverLook != null ? this.serverLook : RotationUtils.entityRotation((Entity)this.player);
            Vec3d direction = Vec3d.fromPolar((float)from.pitch(), (float)from.yaw());
            Vec3d center = box.getCenter();
            boolean flat = box.getLengthY() < 1.0E-6 && direction.y < -1.0E-6 && this.aimEye.y > box.minY;
            double d = along = flat ? (box.minY - this.aimEye.y) / direction.y : center.subtract(this.aimEye).dotProduct(direction);
            if (along <= 0.0) {
                return center;
            }
            Vec3d on = this.aimEye.add(direction.multiply(along));
            return new Vec3d(MathHelper.clamp((double)on.x, (double)box.minX, (double)box.maxX), MathHelper.clamp((double)on.y, (double)box.minY, (double)box.maxY), MathHelper.clamp((double)on.z, (double)box.minZ, (double)box.maxZ));
        }
    }

        private record Plan(Step step, EndCrystalEntity crystal, BlockPos clicked, BlockPos target, Vec3d aim, int route) {
        Plan withRoute(int newRoute) {
            return new Plan(this.step, this.crystal, this.clicked, this.target, this.aim, newRoute);
        }
    }

        private static enum Step {
        BREAK,
        CRYSTAL,
        OBSIDIAN;

    }

        private static final class Side {
        final List<CrystalDamage.Victim> enemies = new ArrayList<CrystalDamage.Victim>();
        final List<CrystalDamage.Victim> friends = new ArrayList<CrystalDamage.Victim>();
        final CrystalDamage.Victim self;

        Side(ClientPlayerEntity player, List<LivingEntity> targets, List<LivingEntity> friends, int lead, int selfLead) {
            for (LivingEntity target : targets) {
                this.enemies.add(new CrystalDamage.Victim(target, Combo.lead(target, lead)));
            }
            for (LivingEntity friend : friends) {
                this.friends.add(new CrystalDamage.Victim(friend, Combo.lead(friend, lead)));
            }
            this.self = new CrystalDamage.Victim((LivingEntity)player, Combo.lead((LivingEntity)player, selfLead));
        }
    }

        private record Candidate(Plan plan, float bound) {
    }

    /** The anchor aura of the original client is not part of abcxyz: this stand-in never fires. */
    private static final class AnchorAuraStub {
        boolean isEnabled() {
            return false;
        }

        List<Combo.Shot> shots(MinecraftClient mc, ClientPlayerEntity player, List<LivingEntity> enemies, List<LivingEntity> friends, int rtt, float rival) {
            return List.of();
        }

        void show(Combo.Shot shot) {
        }

        boolean ready(MinecraftClient mc, ClientPlayerEntity player, Combo.Shot shot) {
            return false;
        }

        void fire(MinecraftClient mc, ClientPlayerEntity player, Combo.Shot shot) {
        }
    }
}
