package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

/**
 * One-shot module: bind it to a key. Each press swaps to a firework rocket, uses it and swaps back.
 */
public class FireworkSwap extends Module {
    public static FireworkSwap INSTANCE;

    private final BooleanSetting onlyFlying = add(new BooleanSetting("OnlyFlying", true));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting packetInteract = add(new BooleanSetting("PacketInteract", false));

    public FireworkSwap() {
        super("FireworkSwap", "Uses a firework rocket without changing your held item", Category.Movement);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        try {
            if (onlyFlying.getValue() && !mc.player.isFallFlying()) return;
            int slot;
            if (mc.player.getMainHandStack().getItem() == Items.FIREWORK_ROCKET) {
                use();
            } else if (inventory.getValue() && (slot = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET)) != -1) {
                InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
                use();
                InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
                EntityUtil.syncInventory();
            } else if ((slot = InventoryUtil.findItem(Items.FIREWORK_ROCKET)) != -1) {
                int old = mc.player.getInventory().selectedSlot;
                InventoryUtil.switchToSlot(slot);
                use();
                InventoryUtil.switchToSlot(old);
            } else {
                sendMessage("§cNo firework rockets found");
            }
        } finally {
            disable();
        }
    }

    private void use() {
        if (packetInteract.getValue()) {
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
        } else {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }
    }
}
