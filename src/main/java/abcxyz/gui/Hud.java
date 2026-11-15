package abcxyz.gui;

import abcxyz.AbcxyzClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class Hud {
    private Hud() {
    }

    public static void render(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (AbcxyzClient.modules == null || mc.options.hudHidden || mc.player == null) return;
        AbcxyzClient.modules.hud(context);
    }
}
