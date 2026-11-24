/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.crystalaura;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.Util;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

public final class LootGuard {
    private static final double HALF_WIDTH = 8.0;
    private static final double HALF_HEIGHT = 4.0;
    private static final double WATCH_RANGE = 16.0;
    private static final double LOADED_RANGE = 32.0;
    private static final long OTHER_MS = 60000L;
    private static final long OWN_MS = 300000L;
    private static final long EMPTY_GRACE_MS = 3000L;
    private static final List<Spot> SPOTS = new ArrayList<Spot>();
    private static final IntSet SEEN = new IntOpenHashSet();
    private static RegistryKey<World> dimension;
    private static ClientPlayerEntity lastPlayer;
    private static int lastTick;
    private static boolean selfDead;

    private LootGuard() {
    }

    public static void update(ClientWorld level, ClientPlayerEntity player) {
        if (player == lastPlayer && player.age == lastTick) {
            return;
        }
        lastPlayer = player;
        lastTick = player.age;
        dimension = level.getRegistryKey();
        long now = Util.getMeasuringTimeMs();
        if (player.isDead()) {
            if (!selfDead) {
                SPOTS.add(new Spot(dimension, player.getPos(), now, now + 300000L));
            }
            selfDead = true;
        } else {
            selfDead = false;
        }
        SEEN.removeIf(id -> level.getEntityById(id) == null);
        for (PlayerEntity other : level.getPlayers()) {
            if (other == player || !other.isDead() || !SEEN.add(other.getId()) || !((double)other.distanceTo((Entity)player) <= 16.0)) continue;
            SPOTS.add(new Spot(dimension, other.getPos(), now, now + 60000L));
        }
        SPOTS.removeIf(spot -> now > spot.until() || spot.dimension() == dimension && now - spot.since() > 3000L && spot.at().distanceTo(player.getPos()) <= 32.0 && level.getNonSpectatingEntities(ItemEntity.class, spot.box()).isEmpty());
    }

    public static boolean near(Vec3d center) {
        for (Spot spot : SPOTS) {
            if (spot.dimension() != dimension || !spot.box().contains(center)) continue;
            return true;
        }
        return false;
    }

    static {
        lastTick = -1;
    }

        private record Spot(RegistryKey<World> dimension, Vec3d at, long since, long until) {
        Box box() {
            return new Box(this.at.x - 8.0, this.at.y - 4.0, this.at.z - 8.0, this.at.x + 8.0, this.at.y + 4.0, this.at.z + 8.0);
        }
    }
}

