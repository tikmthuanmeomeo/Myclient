/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.crystalaura;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;

public final class HurtClock {
    private static final int NEVER = -1000;
    private static final int WINDOW = 10;
    private static final int MAX_WAIT = 20;
    private static final int MAX_MARGIN = 2;
    private static final int RELAX_STREAK = 8;
    private static final int REMOVE_GRACE = 3;
    private static final double ANCHOR_MATCH_SQR = 0.25;
    private static final Int2ObjectOpenHashMap<Mark> MARKS = new Int2ObjectOpenHashMap();
    private static ClientPlayerEntity lastPlayer;
    private static World lastLevel;
    private static Packet<?> lastPacket;
    private static int now;
    private static int margin;
    private static int streak;

    private HurtClock() {
    }

    public static void note(ClientPlayerEntity player, Packet<?> packet) {
        if (packet == lastPacket) {
            return;
        }
        lastPacket = packet;
        if (packet instanceof BundleS2CPacket) {
            BundleS2CPacket bundle = (BundleS2CPacket)packet;
            for (Packet inner : bundle.getPackets()) {
                HurtClock.handle(player, inner);
            }
        } else {
            HurtClock.handle(player, packet);
        }
    }

    public static void tick(ClientPlayerEntity player) {
        now = player.age;
        if (player != lastPlayer || player.getWorld() != lastLevel) {
            MARKS.clear();
            margin = 0;
            streak = 0;
            lastPlayer = player;
            lastLevel = player.getWorld();
        }
        ObjectIterator iterator = MARKS.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            LivingEntity target;
            Int2ObjectMap.Entry entry = (Int2ObjectMap.Entry)iterator.next();
            Entity class_12972 = lastLevel.getEntityById(entry.getIntKey());
            if (!(class_12972 instanceof LivingEntity) || !(target = (LivingEntity)class_12972).isAlive()) {
                iterator.remove();
                continue;
            }
            Mark mark = (Mark)entry.getValue();
            if (mark.removedAt == -1000 || mark.confirmed || now - mark.removedAt <= 3) continue;
            margin = Math.min(2, margin + 1);
            streak = 0;
            mark.shotTick = -1000;
            mark.removedAt = -1000;
        }
    }

    public static boolean freshAt(LivingEntity target, int ahead, int lead) {
        if (target.timeUntilRegen - ahead - lead > 10) {
            return false;
        }
        Mark mark = (Mark)MARKS.get(target.getId());
        return mark == null || mark.shotTick == -1000 || now + ahead - mark.shotTick >= 10 + margin;
    }

    public static int opensIn(LivingEntity target, int lead) {
        for (int ahead = 0; ahead < 20; ++ahead) {
            if (!HurtClock.freshAt(target, ahead, lead)) continue;
            return ahead;
        }
        return 20;
    }

    public static float effective(CrystalDamage.Victim victim, float raw, int ahead, int lead) {
        LivingEntity target = victim.entity();
        if (HurtClock.freshAt(target, ahead, lead)) {
            return victim.reduce(raw);
        }
        Mark mark = (Mark)MARKS.get(target.getId());
        float last = mark == null ? Float.MAX_VALUE : mark.lastRaw;
        return raw > last ? victim.reduce(raw - last) : 0.0f;
    }

    public static void shot(ClientPlayerEntity player, LivingEntity target, float raw, Object key, int lead) {
        Mark mark = (Mark)MARKS.computeIfAbsent(target.getId(), id -> new Mark());
        if (HurtClock.freshAt(target, 0, lead)) {
            mark.shotTick = player.age;
            mark.shotRaw = raw;
            mark.lastRaw = raw;
            mark.shotKey = key;
            mark.confirmed = false;
            mark.removedAt = -1000;
        } else {
            mark.lastRaw = Math.max(mark.lastRaw, raw);
        }
    }

    private static void handle(ClientPlayerEntity player, Packet<?> packet) {
        if (packet instanceof EntityDamageS2CPacket) {
            EntityDamageS2CPacket damage = (EntityDamageS2CPacket)packet;
            HurtClock.damaged(player, damage);
        } else if (packet instanceof EntitiesDestroyS2CPacket) {
            EntitiesDestroyS2CPacket remove = (EntitiesDestroyS2CPacket)packet;
            HurtClock.removed(remove.getEntityIds());
        }
    }

    private static void damaged(ClientPlayerEntity player, EntityDamageS2CPacket packet) {
        PlayerEntity target;
        Entity class_12972 = player.getWorld().getEntityById(packet.entityId());
        if (!(class_12972 instanceof PlayerEntity) || !MeleeAttacks.isEnemy(player, (Entity)(target = (PlayerEntity)class_12972))) {
            return;
        }
        Mark mark = (Mark)MARKS.computeIfAbsent(target.getId(), id -> new Mark());
        if (mark.shotTick != -1000 && !mark.confirmed && HurtClock.ours(player, mark, packet)) {
            mark.lastRaw = mark.shotRaw;
            mark.confirmed = true;
            if (mark.shotKey instanceof Integer && ++streak >= 8) {
                margin = Math.max(0, margin - 1);
                streak = 0;
            }
            return;
        }
        RegistryEntry type = packet.sourceType();
        mark.shotTick = -1000;
        mark.removedAt = -1000;
        mark.lastRaw = type.matchesKey(DamageTypes.PLAYER_ATTACK) || type.matchesKey(DamageTypes.MOB_ATTACK) ? 0.0f : Float.MAX_VALUE;
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private static boolean ours(ClientPlayerEntity player, Mark mark, EntityDamageS2CPacket packet) {
        RegistryEntry type = packet.sourceType();
        if (type.matchesKey(DamageTypes.PLAYER_EXPLOSION)) {
            if (packet.sourceCauseId() != player.getId()) return false;
            Object object = mark.shotKey;
            if (!(object instanceof Integer)) return false;
            Integer crystal = (Integer)object;
            if (packet.sourceDirectId() != crystal.intValue()) return false;
            return true;
        }
        if (!type.matchesKey(DamageTypes.BAD_RESPAWN_POINT)) return false;
        Object object = mark.shotKey;
        if (!(object instanceof Vec3d)) return false;
        Vec3d center = (Vec3d)object;
        if (!packet.sourcePosition().filter(at -> at.squaredDistanceTo(center) < 0.25).isPresent()) return false;
        return true;
    }

    private static void removed(IntList ids) {
        for (Mark mark : MARKS.values()) {
            Integer crystal;
            Object object;
            if (mark.shotTick == -1000 || mark.confirmed || !((object = mark.shotKey) instanceof Integer) || !ids.contains((crystal = (Integer)object).intValue())) continue;
            mark.removedAt = now;
        }
    }

        private static final class Mark {
        float lastRaw = Float.MAX_VALUE;
        int shotTick = -1000;
        float shotRaw;
        Object shotKey;
        boolean confirmed;
        int removedAt = -1000;

        private Mark() {
        }
    }
}

