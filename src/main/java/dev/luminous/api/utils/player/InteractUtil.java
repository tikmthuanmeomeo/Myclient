package dev.luminous.api.utils.player;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

import static dev.luminous.api.utils.Wrapper.mc;

public class InteractUtil {
    public static Entity getEntity(PlayerInteractEntityC2SPacket packet) {
        return mc.world == null ? null : mc.world.getEntityById(packet.entityId);
    }

    public static PlayerInteractEntityC2SPacket.InteractType getInteractType(PlayerInteractEntityC2SPacket packet) {
        return packet.type.getType();
    }
}
