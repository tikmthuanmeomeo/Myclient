package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.asm.accessors.IPlayerMoveC2SPacket;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.BowBomb;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class NoFall extends Module {
    private final EnumSetting<NoFallMode> mode = add(new EnumSetting<>("Mode", NoFallMode.Packet));
    private final SliderSetting distance =
            add(new SliderSetting("Distance", 3.0f, 0.0f, 8.0f, 0.1));

    public NoFall() {
        super("NoFall", "Prevents fall damage.", Category.Player);
        setChinese("没有摔落伤害");
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    public enum NoFallMode {
        Packet,
        Grim
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) {
            return;
        }
        if (mode.is(NoFallMode.Grim) && checkFalling()) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY() + 1.0e-9,
                    mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), false));
            mc.player.onLanding();
        }
    }

    private boolean checkFalling() {
        return mc.player.fallDistance > mc.player.getSafeFallDistance() && !mc.player.isOnGround()
                && !mc.player.isFallFlying();
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) {
            return;
        }
        for (ItemStack is : mc.player.getArmorItems()) {
            if (is.getItem() == Items.ELYTRA) {
                return;
            }
        }
        if (!mode.is(NoFallMode.Packet)) {
            return;
        }
        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (mc.player.fallDistance >= (float) this.distance.getValue() && !BowBomb.send) {
                ((IPlayerMoveC2SPacket) packet).setOnGround(true);
            }
        }
    }
}
