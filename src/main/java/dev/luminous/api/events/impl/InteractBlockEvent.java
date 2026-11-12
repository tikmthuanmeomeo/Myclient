package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.util.Hand;

public class InteractBlockEvent extends Event {
    private InteractBlockEvent() {
    }

    private static final InteractBlockEvent INSTANCE = new InteractBlockEvent();
    public Hand hand;

    public static InteractBlockEvent getPre(Hand hand) {
        INSTANCE.hand = hand;
        INSTANCE.stage = Stage.Pre;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }

    public static InteractBlockEvent getPost(Hand hand) {
        INSTANCE.hand = hand;
        INSTANCE.stage = Stage.Post;
        return INSTANCE;
    }
}
