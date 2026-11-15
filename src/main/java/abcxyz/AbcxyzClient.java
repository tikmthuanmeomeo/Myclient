package abcxyz;

import abcxyz.core.ModuleManager;
import abcxyz.gui.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class AbcxyzClient implements ClientModInitializer {
    public static final String NAME = "abcxyz";
    public static ModuleManager modules;

    @Override
    public void onInitializeClient() {
        modules = new ModuleManager();
        ClientTickEvents.END_CLIENT_TICK.register(client -> modules.tick());
        HudRenderCallback.EVENT.register((context, tickCounter) -> Hud.render(context));
        WorldRenderEvents.LAST.register(modules::render3D);
    }
}
