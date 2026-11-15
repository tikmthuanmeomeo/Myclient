package abcxyz.module.movement;

import abcxyz.core.Category;
import abcxyz.core.Module;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class NoFall extends Module {
    public NoFall() {
        super("NoFall", "Tells the server you are on the ground when you fall", Category.Movement);
    }

    @Override
    public void onTick() {
        if (mc.player.fallDistance > 2.5f && !mc.player.isFallFlying() && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }
    }
}
