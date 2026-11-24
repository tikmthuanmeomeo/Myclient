/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.module.combat;

import abcxyz.AbcxyzClient;
import abcxyz.compat.Render3DEvent;
import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.crystalaura.AnchorGuard;
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
import abcxyz.crystalaura.SlotReturn;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

public final class AnchorAura extends Module {
    private static final double REACH_MARGIN = 0.01;
    private static final int ARM_TICKS = 2;
    private static final int HOLD_TICKS = 10;
    private static final int MAX_LEAD_TICKS = 5;
    private static final int AIM_TIMEOUT_TICKS = 12;
    private static final int SKIP_TICKS = 40;
    private final NumSetting minDamage = this.num("min-damage", "Min Damage", 6.0, 0.0, 20.0, 0.5);
    private final NumSetting maxSelfDamage = this.num("max-self-damage", "Max Self Damage", 6.0, 0.0, 20.0, 0.5);
    private final BoolSetting render = this.bool("render", "Render", true);
    private final int renderColor = -26052;
    private final NumSetting turnSpeed = this.num("turn-speed", "Turn Speed", 4.5, 1.0, 10.0, 0.5);
    private final BoolSetting protectLoot = this.bool("protect-loot", "Protect Loot", true);
    private final SlotReturn slots = new SlotReturn(this);
    private final ShotView view = new ShotView();
    private int tick;
    private int holdUntil;
    private BlockPos spent;
    private int spentUntil;
    private float aimTurn;
    private float placedTurn;
    private BlockPos aimingAt;
    private int aimingTicks;
    private BlockPos skipped;
    private int skippedUntil;
    private List<CrystalDamage.Victim> lastTargets;
    private List<CrystalDamage.Victim> lastSpared;
    private int lastRtt;
    private float steerMin;
    private float steerSelf;
    private int steerUntil = -1;
    private float tickMin;
    private float tickSelf;

    public AnchorAura() {
        super("AnchorAura", "Places, charges and detonates respawn anchors on nearby enemies", Category.Combat);
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

    @Override
    public void onDisable() {
        this.slots.drop(MinecraftClient.getInstance(), true);
        this.spent = null;
        this.view.clear();
    }

    @Override
    public void onTick() {
        CrystalAura crystal = AbcxyzClient.modules == null ? null : AbcxyzClient.modules.get(CrystalAura.class);
        ++this.tick;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player != null && mc.world != null) {
            LootGuard.update(mc.world, player);
        }
        if (crystal != null && crystal.isEnabled() || !MeleeAttacks.canAct(mc) || player.isSpectator() || mc.world.getRegistryKey() == World.NETHER) {
            this.slots.tick(mc, true);
            this.release();
            return;
        }
        HurtClock.tick(player);
        if (this.tick > this.holdUntil) {
            this.slots.tick(mc, true);
        }
        if (MeleeAttacks.actedThisTick((PlayerEntity)player) || !MeleeAttacks.handsFree(mc, player) || player.shouldCancelInteraction() || MeleeAttacks.rightClickComing(mc)) {
            return;
        }
        Combo.Shot shot = Combo.pick(this.shots(mc, player, Combo.enemies(mc, player), Combo.friends(mc, player), Crystals.rttTicks(mc, player), 0.0f));
        if (shot == null) {
            if (this.spent != null && this.tick < this.spentUntil && mc.world.getBlockState(this.spent).isOf(Blocks.RESPAWN_ANCHOR)) {
                ServerRotation.request(ServerRotation.sent());
            }
            if (this.tick > this.holdUntil) {
                this.release();
            }
            return;
        }
        this.show(shot);
        if (!this.ready(mc, player, shot)) {
            BlockPos cell = AnchorAura.cellOf(shot);
            this.aimingTicks = cell.equals((Object)this.aimingAt) ? this.aimingTicks + 1 : 0;
            this.aimingAt = cell;
            if (this.aimingTicks >= 12) {
                this.skipped = cell;
                this.skippedUntil = this.tick + 40;
                this.aimingAt = null;
                return;
            }
            ServerRotation.request(RotationUtils.fromTo(player.getEyePos(), shot.aim()), ((float) this.turnSpeed.d()));
            return;
        }
        this.aimingAt = null;
        if (shot.route() != -2 && !this.slots.take(player, shot.route())) {
            return;
        }
        this.fire(mc, player, shot);
        this.holdUntil = this.tick + 10;
        ServerRotation.request(ServerRotation.sent());
    }

    /** Packets that matter to the anchor aura (called from ClientPlayNetworkHandlerMixin on the main thread). */
    public void onPacket(Packet<?> incoming) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            HurtClock.note(player, incoming);
        }
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

    private static BlockPos cellOf(Combo.Shot shot) {
        BlockPos class_23382;
        Object object = shot.plan();
        if (object instanceof AnchorGuard.Spot) {
            AnchorGuard.Spot spot = (AnchorGuard.Spot)object;
            class_23382 = spot.pos();
        } else {
            class_23382 = (BlockPos)shot.plan();
        }
        return class_23382;
    }

    private boolean ruledOut(BlockPos cell) {
        return cell.equals((Object)this.skipped) && this.tick < this.skippedUntil || this.protectLoot.on() && LootGuard.near(Vec3d.ofCenter((Vec3i)cell));
    }

    public void show(Combo.Shot shot) {
        if (!this.render.on()) {
            return;
        }
        BlockPos cell = AnchorAura.cellOf(shot);
        Vec3d center = Vec3d.ofCenter((Vec3i)cell);
        float most = 0.0f;
        for (CrystalDamage.Victim victim : this.lastTargets) {
            most = Math.max(most, victim.damage(center, 5.0f, cell));
        }
        float own = this.lastSpared.get(0).damage(center, 5.0f, cell);
        this.view.show(new Box(cell), this.renderColor, most, own);
    }

    public void steer(ClientPlayerEntity player, float minDamage, float maxSelf) {
        this.steerMin = minDamage;
        this.steerSelf = maxSelf;
        this.steerUntil = player.age + 2;
    }

    public List<Combo.Shot> shots(MinecraftClient mc, ClientPlayerEntity player, List<LivingEntity> enemies, List<LivingEntity> friends, int rtt, float rival) {
        List<Box> reserved;
        Combo.Shot placement;
        ClientWorld level = mc.world;
        boolean steered = player.age <= this.steerUntil;
        this.tickMin = steered ? this.steerMin : ((float) this.minDamage.d());
        this.tickSelf = steered ? this.steerSelf : ((float) this.maxSelfDamage.d());
        PlayerInventory inventory = player.getInventory();
        ItemStack main = player.getMainHandStack();
        int detonator = AnchorGuard.detonatorRoute(inventory, main);
        if (level.getRegistryKey() == World.NETHER || !ServerRotation.canRotate(player) || player.getOffHandStack().isOf(Items.GLOWSTONE) || player.shouldCancelInteraction() || enemies.isEmpty() || detonator == -1) {
            return List.of();
        }
        ArrayList<LivingEntity> spared = new ArrayList<LivingEntity>();
        spared.add((LivingEntity)player);
        spared.addAll(friends);
        this.lastRtt = rtt;
        this.lastTargets = AnchorAura.victims(enemies, rtt);
        this.lastSpared = AnchorAura.victims(spared, rtt);
        ArrayList<Combo.Shot> shots = new ArrayList<Combo.Shot>();
        int glowstone = AnchorAura.routeOf(inventory, main, Items.GLOWSTONE);
        boolean existing = false;
        for (BlockPos cell : this.anchors(level, player, enemies)) {
            boolean charged;
            int ahead;
            Blast due;
            Vec3d aim;
            if (this.ruledOut(cell) || (aim = AnchorGuard.anchorAim(level, player, cell)) == null || !this.safe(this.lastSpared, cell) || (due = this.blast(cell, ahead = (charged = (Integer)level.getBlockState(cell).get((Property)RespawnAnchorBlock.CHARGES) > 0) ? 0 : 1, false)) == null && this.blast(cell, ahead, true) == null) continue;
            existing = true;
            if (due == null) continue;
            if (charged) {
                shots.add(new Combo.Shot(Combo.Kind.DETONATE, aim, detonator, due.value(), due.kills(), 0, cell));
                continue;
            }
            if (glowstone == -1 || HurtClock.opensIn(due.source(), rtt) > 1 || !(rival <= 0.0f) && !(due.value() > rival + 1.0f)) continue;
            shots.add(new Combo.Shot(Combo.Kind.CHARGE, aim, glowstone, due.value(), due.kills(), 1, cell));
        }
        int anchor = AnchorAura.routeOf(inventory, main, Items.RESPAWN_ANCHOR);
        if (!existing && anchor != -1 && glowstone != -1 && (placement = this.place(level, player, anchor, reserved = Crystals.reservedBoxes(player, Crystals.confirmWindow(mc, player)))) != null) {
            shots.add(placement);
        }
        return shots;
    }

    public boolean ready(MinecraftClient mc, ClientPlayerEntity player, Combo.Shot shot) {
        boolean charged;
        ClientWorld level = mc.world;
        if (shot.kind() == Combo.Kind.ANCHOR) {
            AnchorGuard.Spot spot = (AnchorGuard.Spot)shot.plan();
            Rotation sent = ServerRotation.sent();
            if (sent != null && AnchorGuard.hitAlong(level, player, sent, spot) != null) {
                return true;
            }
            float turn = AnchorAura.turnTo(player, spot);
            if (turn > 0.0f) {
                this.aimTurn = turn;
            }
            return false;
        }
        BlockPos cell = (BlockPos)shot.plan();
        BlockState state = level.getBlockState(cell);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR) || AnchorGuard.anchorHit(level, player, cell) == null) {
            return false;
        }
        boolean bl = charged = (Integer)state.get((Property)RespawnAnchorBlock.CHARGES) > 0;
        if (shot.kind() == Combo.Kind.CHARGE) {
            return !charged;
        }
        return charged && this.safe(this.lastSpared, cell) && this.blast(cell, 0, false) != null;
    }

    public void fire(MinecraftClient mc, ClientPlayerEntity player, Combo.Shot shot) {
        ClientWorld level = mc.world;
        switch (shot.kind()) {
            case ANCHOR: {
                AnchorGuard.Spot spot = (AnchorGuard.Spot)shot.plan();
                AnchorGuard.click(mc, player, AnchorGuard.hitAlong(level, player, ServerRotation.sent(), spot));
                if (!(this.aimTurn > 0.0f)) break;
                this.placedTurn = this.aimTurn;
                break;
            }
            case CHARGE: {
                AnchorGuard.click(mc, player, AnchorGuard.anchorHit(level, player, (BlockPos)shot.plan()));
                break;
            }
            case DETONATE: {
                BlockPos cell = (BlockPos)shot.plan();
                AnchorGuard.click(mc, player, AnchorGuard.anchorHit(level, player, cell));
                this.spent = cell;
                this.spentUntil = this.tick + Crystals.confirmWindow(mc, player);
                Vec3d center = Vec3d.ofCenter((Vec3i)cell);
                for (CrystalDamage.Victim victim : this.lastTargets) {
                    float raw = victim.raw(center, 5.0f, cell);
                    if (!(raw > 0.0f)) continue;
                    HurtClock.shot(player, victim.entity(), raw, center, this.lastRtt);
                }
                break;
            }
        }
    }

    private Combo.Shot place(ClientWorld level, ClientPlayerEntity player, int route, List<Box> reserved) {
        LinkedHashMap<BlockPos, Float> bounds = new LinkedHashMap<BlockPos, Float>();
        for (CrystalDamage.Victim victim : this.lastTargets) {
            BlockPos feet = victim.entity().getBlockPos();
            for (BlockPos pos : BlockPos.iterate((BlockPos)feet.add(-2, -1, -2), (BlockPos)feet.add(2, 2, 2))) {
                float bound;
                if (bounds.containsKey(pos) || !level.getBlockState(pos).isAir() || !player.canInteractWithBlockAt(pos, -0.01) || AnchorAura.reservedAt(reserved, pos) || this.ruledOut(pos) || this.walkedInto(level, pos) || !((bound = this.upperValue(Vec3d.ofCenter((Vec3i)pos))) >= 0.0f)) continue;
                bounds.put(pos.toImmutable(), Float.valueOf(bound));
            }
        }
        ArrayList<BlockPos> cells = new ArrayList<BlockPos>(bounds.keySet());
        cells.sort((a, b) -> Float.compare(((Float)bounds.get(b)).floatValue(), ((Float)bounds.get(a)).floatValue()));
        Vec3d eye = player.getEyePos();
        Combo.Shot best = null;
        for (BlockPos pos : cells) {
            float turn;
            Rotation look;
            Vec3d reach;
            AnchorGuard.Spot candidate;
            if (best != null && ((Float)bounds.get(pos)).floatValue() <= best.value()) break;
            Blast blast = this.blast(pos, 2, true);
            if (blast == null || best != null && blast.value() <= best.value() || !this.safe(this.lastSpared, pos) || (candidate = AnchorGuard.supportFor(level, player, eye, pos)) == null || new Box(pos).raycast(eye, eye.add(reach = Vec3d.fromPolar((float)(look = ServerRotation.next(player, RotationUtils.fromTo(eye, candidate.aim()))).pitch(), (float)look.yaw()).multiply(player.getBlockInteractionRange()))).isEmpty() || (turn = AnchorAura.turnTo(player, candidate)) > 2.0f && Math.abs(turn - this.placedTurn) < 1.0E-4f) continue;
            best = new Combo.Shot(Combo.Kind.ANCHOR, candidate.aim(), route, blast.value(), blast.kills(), 2, candidate);
        }
        return best;
    }

    private Set<BlockPos> anchors(ClientWorld level, ClientPlayerEntity player, List<LivingEntity> enemies) {
        LinkedHashSet<BlockPos> anchors = new LinkedHashSet<BlockPos>();
        for (LivingEntity enemy : enemies) {
            BlockPos feet = enemy.getBlockPos();
            for (BlockPos pos : BlockPos.iterate((BlockPos)feet.add(-4, -3, -4), (BlockPos)feet.add(4, 4, 4))) {
                if (!level.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR) || !player.canInteractWithBlockAt(pos, -0.01) || pos.equals((Object)this.spent) && this.tick < this.spentUntil) continue;
                anchors.add(pos.toImmutable());
            }
        }
        return anchors;
    }

    private Blast blast(BlockPos cell, int ahead, boolean waiting) {
        Vec3d center = Vec3d.ofCenter((Vec3i)cell);
        LivingEntity source = null;
        float best = 0.0f;
        boolean kills = false;
        for (CrystalDamage.Victim victim : this.lastTargets) {
            int when;
            LivingEntity enemy = victim.entity();
            int n = when = waiting ? Math.max(ahead, HurtClock.opensIn(enemy, this.lastRtt)) : ahead;
            float effective = HurtClock.effective(victim, victim.raw(center, 5.0f, cell), when, this.lastRtt);
            if (effective < this.needed(enemy)) continue;
            kills = kills || Combo.kills(effective, enemy);
            float value = Combo.value(effective, enemy);
            if (source != null && !(value > best)) continue;
            source = enemy;
            best = value;
        }
        if (source == null) {
            return null;
        }
        float own = this.lastSpared.get(0).damage(center, 5.0f, cell);
        return new Blast(best - 0.5f * own, kills, source);
    }

    private float upperValue(Vec3d center) {
        float bound = -1.0f;
        for (CrystalDamage.Victim victim : this.lastTargets) {
            float upper = victim.upperBound(center, 5.0f);
            if (!(upper >= this.needed(victim.entity()))) continue;
            bound = Math.max(bound, Combo.value(upper, victim.entity()));
        }
        return bound;
    }

    private boolean safe(List<CrystalDamage.Victim> spared, BlockPos cell) {
        Vec3d center = Vec3d.ofCenter((Vec3i)cell);
        for (CrystalDamage.Victim other : spared) {
            float damage = other.damage(center, 5.0f, cell);
            if (!(damage > this.tickSelf) && !CrystalDamage.endangers((PlayerEntity)other.entity(), damage)) continue;
            return false;
        }
        return true;
    }

    private float needed(LivingEntity enemy) {
        return Math.min(this.tickMin, CrystalDamage.health(enemy));
    }

    private static List<CrystalDamage.Victim> victims(List<LivingEntity> entities, int rtt) {
        ArrayList<CrystalDamage.Victim> victims = new ArrayList<CrystalDamage.Victim>(entities.size());
        for (LivingEntity entity : entities) {
            int lead = entity instanceof ClientPlayerEntity ? 0 : Math.min(rtt, 5);
            victims.add(new CrystalDamage.Victim(entity, Combo.lead(entity, lead)));
        }
        return victims;
    }

    private boolean walkedInto(ClientWorld level, BlockPos pos) {
        Box cell = new Box(pos);
        int lead = Math.min(this.lastRtt, 5);
        for (PlayerEntity other : level.getPlayers()) {
            Box box;
            if (other.isSpectator() || other instanceof ClientPlayerEntity || !(box = other.getBoundingBox()).intersects(cell) && !box.offset(Combo.lead((LivingEntity)other, lead)).intersects(cell)) continue;
            return true;
        }
        return false;
    }

    private static boolean reservedAt(List<Box> reserved, BlockPos pos) {
        for (Box box : reserved) {
            if (!box.intersects(new Box(pos))) continue;
            return true;
        }
        return false;
    }

    private static int routeOf(PlayerInventory inventory, ItemStack main, Item item) {
        if (main.isOf(item)) {
            return -2;
        }
        int slot = Crystals.hotbarSlotOf(inventory, item);
        return slot == -1 ? -1 : slot;
    }

    private static float turnTo(ClientPlayerEntity player, AnchorGuard.Spot spot) {
        Rotation sent = ServerRotation.sent();
        Rotation from = sent != null ? sent : RotationUtils.entityRotation((Entity)player);
        Rotation aimed = ServerRotation.next(player, RotationUtils.fromTo(player.getEyePos(), spot.aim()));
        return Math.abs(aimed.yaw() - from.yaw());
    }

    private void release() {
    }

        private record Blast(float value, boolean kills, LivingEntity source) {
    }
}

