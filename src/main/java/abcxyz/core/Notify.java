package abcxyz.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** A chat line only you can see: [prefix] message. */
public final class Notify {
    private Notify() {
    }

    public static void chat(String prefix, String message) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) mc.player.sendMessage(Text.literal("[" + prefix + "] " + message), false);
        } catch (Throwable ignored) {
        }
    }
}
