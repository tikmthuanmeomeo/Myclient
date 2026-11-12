package dev.luminous.mod.modules.impl.player;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.Map;

public class AutoArmor extends Module {
    public static AutoArmor INSTANCE;
    private final EnumSetting<EnchantPriority> head = add(new EnumSetting<>("Head", EnchantPriority.Protection));
    private final EnumSetting<EnchantPriority> body = add(new EnumSetting<>("Body", EnchantPriority.Protection));
    private final EnumSetting<EnchantPriority> tights = add(new EnumSetting<>("Tights", EnchantPriority.Protection));
    private final EnumSetting<EnchantPriority> feet = add(new EnumSetting<>("Feet", EnchantPriority.Protection));
    private final BooleanSetting ignoreCurse = add(new BooleanSetting("IgnoreCurse", true));
    private final BooleanSetting noMove =
            add(new BooleanSetting("NoMove", false));
    private final SliderSetting delay =
            add(new SliderSetting("Delay", 3, 0, 10, 1));
    private final BooleanSetting autoElytra =
            add(new BooleanSetting("AutoElytra", true));
    private final EnumSetting<HotbarSwapMode> hotbarSwap = add(new EnumSetting<>("HotbarSwap", HotbarSwapMode.Swap));
    private final EnumSetting<InventorySwapMode> inventorySwap = add(new EnumSetting<>("InventorySwap", InventorySwapMode.ClickSlot));

    private int tickDelay = 0;

    public AutoArmor() {
        super("AutoArmor", Category.Player);
        setChinese("自动穿甲");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!EntityUtil.inInventory()) {
            return;
        }

        if (mc.player.playerScreenHandler != mc.player.currentScreenHandler)
            return;

        if (MovementUtil.isMoving() && noMove.getValue()) return;

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        tickDelay = delay.getValueInt();

        Map<EquipmentSlot, int[]> armorMap = new HashMap<>(4);
        armorMap.put(EquipmentSlot.FEET, new int[]{36, getProtection(mc.player.getInventory().getStack(36)), -1, -1});
        armorMap.put(EquipmentSlot.LEGS, new int[]{37, getProtection(mc.player.getInventory().getStack(37)), -1, -1});
        armorMap.put(EquipmentSlot.CHEST, new int[]{38, getProtection(mc.player.getInventory().getStack(38)), -1, -1});
        armorMap.put(EquipmentSlot.HEAD, new int[]{39, getProtection(mc.player.getInventory().getStack(39)), -1, -1});
        for (int s = 0; s < 36; s++) {
            if (!(mc.player.getInventory().getStack(s).getItem() instanceof ArmorItem) && mc.player.getInventory().getStack(s).getItem() != Items.ELYTRA)
                continue;
            if (mc.player.getInventory().getStack(s).getItem() == Items.ELYTRA) {
                if (ElytraFly.INSTANCE.isOff() && autoElytra.getValue()) continue;
                if (ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.packet.getValue()) continue;
            }
            int protection = getProtection(mc.player.getInventory().getStack(s));
            EquipmentSlot slot = (mc.player.getInventory().getStack(s).getItem() instanceof ElytraItem ? EquipmentSlot.CHEST : ((ArmorItem) mc.player.getInventory().getStack(s).getItem()).getSlotType());
            for (Map.Entry<EquipmentSlot, int[]> e : armorMap.entrySet()) {
                if (autoElytra.getValue() && (ElytraFly.INSTANCE.isOn()) && e.getKey() == EquipmentSlot.CHEST) {
                    if (!mc.player.getInventory().getStack(38).isEmpty() && mc.player.getInventory().getStack(38).getItem() instanceof ElytraItem && ElytraItem.isUsable(mc.player.getInventory().getStack(38))) {
                        continue;
                    }
                    if (e.getValue()[2] != -1 && !mc.player.getInventory().getStack(e.getValue()[2]).isEmpty() && mc.player.getInventory().getStack(e.getValue()[2]).getItem() instanceof ElytraItem && ElytraItem.isUsable(mc.player.getInventory().getStack(e.getValue()[2]))) {
                        continue;
                    }
                    if (!mc.player.getInventory().getStack(s).isEmpty() && mc.player.getInventory().getStack(s).getItem() instanceof ElytraItem && ElytraItem.isUsable(mc.player.getInventory().getStack(s))) {
                        e.getValue()[2] = s;
                    }
                    continue;
                }
                if (protection > 0) {
                    if (e.getKey() == slot) {
                        if (protection > e.getValue()[1] && protection > e.getValue()[3]) {
                            e.getValue()[2] = s;
                            e.getValue()[3] = protection;
                        }
                    }
                }
            }
        }

        for (Map.Entry<EquipmentSlot, int[]> equipmentSlotEntry : armorMap.entrySet()) {
            if (equipmentSlotEntry.getValue()[2] != -1) {
                if (equipmentSlotEntry.getValue()[2] < 9) {
                    switch (hotbarSwap.getValue()) {
                        case Swap -> {
                            int armorSlot = 44 - equipmentSlotEntry.getValue()[0];
                            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, armorSlot, equipmentSlotEntry.getValue()[2], SlotActionType.SWAP, mc.player);
                            EntityUtil.syncInventory();
                        }
                        case Switch -> {
                            int old = mc.player.getInventory().selectedSlot;
                            InventoryUtil.switchToSlot(equipmentSlotEntry.getValue()[2]);
                            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                            InventoryUtil.switchToSlot(old);
                        }
                    }
                } else if (mc.player.playerScreenHandler == mc.player.currentScreenHandler) {
                    int armorSlot = 44 - equipmentSlotEntry.getValue()[0];
                    int newArmorSlot = equipmentSlotEntry.getValue()[2];

                    switch (inventorySwap.getValue()) {
                        case Pick -> {
                            mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(newArmorSlot));
                            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                            mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(newArmorSlot));
                        }
                        case ClickSlot -> {
                            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, newArmorSlot, 0, SlotActionType.PICKUP, mc.player);
                            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, armorSlot, 0, SlotActionType.PICKUP, mc.player);
                            if (equipmentSlotEntry.getValue()[1] != -1)
                                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, newArmorSlot, 0, SlotActionType.PICKUP, mc.player);
                        }
                    }

                    EntityUtil.syncInventory();
                }
                return;
            }
        }
    }

    private int getProtection(ItemStack is) {
        if (is.getItem() instanceof ArmorItem || is.getItem() == Items.ELYTRA) {
            int prot = 0;
            EquipmentSlot slot = is.getItem() instanceof ArmorItem ai ? ai.getSlotType() : EquipmentSlot.BODY;

            if (is.getItem() instanceof ElytraItem) {
                if (!ElytraItem.isUsable(is))
                    return 0;
                prot = 1;
            }

            int blastMultiplier = 1;
            int protectionMultiplier = 1;

            switch (slot) {
                case HEAD -> {
                    if (head.is(EnchantPriority.Protection)) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                case BODY -> {
                    if (body.is(EnchantPriority.Protection)) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                case LEGS -> {
                    if (tights.is(EnchantPriority.Protection)) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                case FEET -> {
                    if (feet.is(EnchantPriority.Protection)) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
            }

            if (is.hasEnchantments()) {
                ItemEnchantmentsComponent enchants = EnchantmentHelper.getEnchantments(is);

                //mc.world.getRegistryManager().get(Enchantments.BLAST_PROTECTION.getRegistryRef()).getEntry(Enchantments.BLAST_PROTECTION).get()
                if (enchants.getEnchantments().contains(mc.world.getRegistryManager().get(Enchantments.PROTECTION.getRegistryRef()).getEntry(Enchantments.PROTECTION).get()))
                    prot += enchants.getLevel(mc.world.getRegistryManager().get(Enchantments.PROTECTION.getRegistryRef()).getEntry(Enchantments.PROTECTION).get()) * protectionMultiplier;

                if (enchants.getEnchantments().contains(mc.world.getRegistryManager().get(Enchantments.BLAST_PROTECTION.getRegistryRef()).getEntry(Enchantments.BLAST_PROTECTION).get()))
                    prot += enchants.getLevel(mc.world.getRegistryManager().get(Enchantments.BLAST_PROTECTION.getRegistryRef()).getEntry(Enchantments.BLAST_PROTECTION).get()) * blastMultiplier;

                if (enchants.getEnchantments().contains(mc.world.getRegistryManager().get(Enchantments.BLAST_PROTECTION.getRegistryRef()).getEntry(Enchantments.BINDING_CURSE).get()) && ignoreCurse.getValue())
                    prot = -999;
            }
            return (is.getItem() instanceof ArmorItem ? ((ArmorItem) is.getItem()).getProtection() : 0) + prot;
        } else if (!is.isEmpty()) {
            return 0;
        }

        return -1;
    }

    private enum EnchantPriority {
        Blast, Protection
    }

    public enum HotbarSwapMode {
        Swap,
        Switch,
    }

    public enum InventorySwapMode {
        ClickSlot,
        Pick,
    }
}