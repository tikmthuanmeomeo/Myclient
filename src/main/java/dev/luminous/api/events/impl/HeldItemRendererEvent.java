package dev.luminous.api.events.impl;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class HeldItemRendererEvent {
    private HeldItemRendererEvent() {
    }

    private static final HeldItemRendererEvent INSTANCE = new HeldItemRendererEvent();
    private Hand hand;
    private ItemStack item;
    private float ep;
    private MatrixStack stack;

    public static HeldItemRendererEvent get(Hand hand, ItemStack item, float equipProgress, MatrixStack stack) {
        INSTANCE.hand = hand;
        INSTANCE.item = item;
        INSTANCE.ep = equipProgress;
        INSTANCE.stack = stack;
        return INSTANCE;
    }

    public Hand getHand() {
        return hand;
    }

    public ItemStack getItem() {
        return item;
    }

    public float getEp() {
        return ep;
    }

    public MatrixStack getStack() {
        return stack;
    }
}