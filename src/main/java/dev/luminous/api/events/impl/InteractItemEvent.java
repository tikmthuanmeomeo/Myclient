package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.util.Hand;

public class InteractItemEvent extends Event {
    private InteractItemEvent() {
    }

    private static final InteractItemEvent INSTANCE = new InteractItemEvent();
    public Hand hand;

    public static InteractItemEvent getPre(Hand hand) {
        INSTANCE.hand = hand;
        INSTANCE.stage = Stage.Pre;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }

    public static InteractItemEvent getPost(Hand hand) {
        INSTANCE.hand = hand;
        INSTANCE.stage = Stage.Post;
        return INSTANCE;
    }
}
