package dev.luminous.mod.modules.impl.combat;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

public class AntiWeak extends Module {
    private final EnumSetting<SwapMode> swapMode =
            add(new EnumSetting<>("SwapMode", SwapMode.Inventory));
    private final SliderSetting delay = add(new SliderSetting("Delay", 100, 0, 500).setSuffix("ms"));
    private final BooleanSetting onlyCrystal =
            add(new BooleanSetting("OnlyCrystal", true));
    private final Timer delayTimer = new Timer();
    boolean ignore = false;
    private PlayerInteractEntityC2SPacket lastPacket = null;

    public AntiWeak() {
        super("AntiWeak", Category.Combat);
        setChinese("反虚弱");
    }

    @Override
    public String getInfo() {
        return swapMode.getValue().name();
    }

    @EventListener(priority = EventPriority.HIGHEST)
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (event.isCancelled()) return;
        if (ignore) return;
        if (mc.player.getStatusEffect(StatusEffects.WEAKNESS) == null) return;
        if (mc.player.getMainHandStack().getItem() instanceof SwordItem)
            return;
        if (!delayTimer.passedMs(delay.getValue())) return;
        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet && Criticals.getInteractType(packet) == PlayerInteractEntityC2SPacket.InteractType.ATTACK) {

            if (onlyCrystal.getValue() && !(Criticals.getEntity(packet) instanceof EndCrystalEntity))
                return;
            lastPacket = packet;
            delayTimer.reset();
            ignore = true;
            doAnti();
            ignore = false;
            event.cancel();
        }
    }

    private void doAnti() {
        if (lastPacket == null) return;
        int strong;
        if (swapMode.getValue() != SwapMode.Inventory) {
            strong = InventoryUtil.findClass(SwordItem.class);
        } else {
            strong = InventoryUtil.findClassInventorySlot(SwordItem.class);
        }
        if (strong == -1) return;
        int old = mc.player.getInventory().selectedSlot;
        if (swapMode.getValue() != SwapMode.Inventory) {
            InventoryUtil.switchToSlot(strong);
        } else {
            InventoryUtil.inventorySwap(strong, mc.player.getInventory().selectedSlot);
        }
        mc.getNetworkHandler().sendPacket(lastPacket);
        if (swapMode.getValue() != SwapMode.Inventory) {
            if (swapMode.getValue() != SwapMode.Normal) InventoryUtil.switchToSlot(old);
        } else {
            InventoryUtil.inventorySwap(strong, mc.player.getInventory().selectedSlot);
            EntityUtil.syncInventory();
        }
    }

    public enum SwapMode {
        Normal, Silent, Inventory
    }
}
