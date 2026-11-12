package dev.luminous.api.events.impl;

import net.minecraft.entity.player.PlayerEntity;

public class TotemEvent {
    private TotemEvent() {
    }

    private static final TotemEvent INSTANCE = new TotemEvent();
    private PlayerEntity player;

    public static TotemEvent get(PlayerEntity player) {
        INSTANCE.player = player;
        return INSTANCE;
    }

    public PlayerEntity getPlayer() {
        return player;
    }
}
