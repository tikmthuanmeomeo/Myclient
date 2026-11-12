package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;

public class InventoryS2CPacketEvent extends Event {
    private InventoryS2CPacketEvent() {
    }

    private static final InventoryS2CPacketEvent INSTANCE = new InventoryS2CPacketEvent();
    public InventoryS2CPacket packet;

    public static InventoryS2CPacketEvent get(InventoryS2CPacket hand) {
        INSTANCE.packet = hand;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}
