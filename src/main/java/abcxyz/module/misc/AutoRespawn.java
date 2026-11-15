package abcxyz.module.misc;

import abcxyz.core.Category;
import abcxyz.core.Module;
import net.minecraft.client.gui.screen.DeathScreen;

public class AutoRespawn extends Module {
    public AutoRespawn() {
        super("AutoRespawn", "Respawns automatically when you die", Category.Misc);
    }

    @Override
    public void onTick() {
        if (mc.currentScreen instanceof DeathScreen && mc.player.isDead()) {
            mc.player.requestRespawn();
            mc.setScreen(null);
        }
    }
}
