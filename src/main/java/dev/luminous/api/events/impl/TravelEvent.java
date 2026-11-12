package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.entity.player.PlayerEntity;

public class TravelEvent extends Event {
    private TravelEvent() {
    }

    private static final TravelEvent INSTANCE = new TravelEvent();
    private PlayerEntity entity;

    public static TravelEvent get(Stage stage, PlayerEntity entity) {
        INSTANCE.entity = entity;
        INSTANCE.stage = stage;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }

    public PlayerEntity getEntity() {
        return entity;
    }
}