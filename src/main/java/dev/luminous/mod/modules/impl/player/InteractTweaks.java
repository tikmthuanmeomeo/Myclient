package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;

public class InteractTweaks extends Module {

    public static InteractTweaks INSTANCE;
    public final BooleanSetting noEntityTrace =
            add(new BooleanSetting("NoEntityTrace", true).setParent());
    public final BooleanSetting onlyPickaxe =
            add(new BooleanSetting("OnlyPickaxe", true, noEntityTrace::isOpen));
    public final BooleanSetting multiTask =
            add(new BooleanSetting("MultiTask", true));
    public final BooleanSetting respawn =
            add(new BooleanSetting("Respawn", true));
    public final BooleanSetting ghostHand =
            add(new BooleanSetting("IgnoreBedrock", false));
    private final BooleanSetting noAbort =
            add(new BooleanSetting("NoMineAbort", false));
    private final BooleanSetting noReset =
            add(new BooleanSetting("NoMineReset", false));
    private final BooleanSetting noDelay =
            add(new BooleanSetting("NoMineDelay", false));
    private final BooleanSetting noInteract =
            add(new BooleanSetting("NoInteract", false));
    private final BooleanSetting pickaxeSwitch =
            add(new BooleanSetting("SwitchEat", false).setParent());
    private final BooleanSetting allowSword =
            add(new BooleanSetting("Sword", true, pickaxeSwitch::isOpen));
    private final BooleanSetting allowPickaxe =
            add(new BooleanSetting("Pickaxe", true, pickaxeSwitch::isOpen));
    private final BooleanSetting reach =
            add(new BooleanSetting("Reach", false));
    public final SliderSetting blockRange = add(new SliderSetting("BlockRange", 5, 0, 15, 0.1, reach::getValue));
    public final SliderSetting entityRange = add(new SliderSetting("EntityRange", 5, 0, 15, 0.1, reach::getValue));

    private final SliderSetting delay = add(new SliderSetting("UseDelay", 4, 0, 4, 1));
    public boolean isActive;
    boolean swapped = false;
    int lastSlot = 0;

    public InteractTweaks() {
        super("InteractTweaks", Category.Player);
        setChinese("交互调整");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (respawn.getValue() && mc.currentScreen instanceof DeathScreen) {
            mc.player.requestRespawn();
            mc.setScreen(null);
        }
        if (mc.itemUseCooldown <= 4 - delay.getValueInt()) {
            mc.itemUseCooldown = 0;
        }
        if (pickaxeSwitch.getValue()) {
            if (!(mc.player.getMainHandStack().getItem() instanceof PickaxeItem && allowPickaxe.getValue()) && (!(mc.player.getMainHandStack().getItem() instanceof SwordItem) || !allowSword.getValue()) && mc.player.getMainHandStack().getItem() != Items.ENCHANTED_GOLDEN_APPLE && mc.player.getMainHandStack().getItem() != Items.GOLDEN_APPLE) {
                swapped = false;
                return;
            }
            int gapple = InventoryUtil.findItem(Items.ENCHANTED_GOLDEN_APPLE);
            if (gapple == -1) {
                gapple = InventoryUtil.findItem(Items.GOLDEN_APPLE);
            }
            if (gapple == -1) {
                if (swapped) {
                    InventoryUtil.switchToSlot(lastSlot);
                    swapped = false;
                }
                return;
            }
            if (mc.options.useKey.isPressed()) {
                if ((mc.player.getMainHandStack().getItem() instanceof PickaxeItem && allowPickaxe.getValue() || (mc.player.getMainHandStack().getItem() instanceof SwordItem && allowSword.getValue())) && mc.player.getOffHandStack().getItem() != Items.ENCHANTED_GOLDEN_APPLE && mc.player.getMainHandStack().getItem() != Items.GOLDEN_APPLE) {
                    lastSlot = mc.player.getInventory().selectedSlot;
                    InventoryUtil.switchToSlot(gapple);
                    swapped = true;
                }
            } else if (swapped) {
                InventoryUtil.switchToSlot(lastSlot);
                swapped = false;
            }
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Send event) {
        if (nullCheck() || !noInteract.getValue() || !(event.getPacket() instanceof PlayerInteractBlockC2SPacket packet)) {
            return;
        }
        Block block = mc.world.getBlockState(packet.getBlockHitResult().getBlockPos()).getBlock();
        if (!mc.player.isSneaking()) {
            if (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof AnvilBlock) {
                event.cancel();
            }
        }
    }

    @Override
    public void onDisable() {
        isActive = false;
    }

    public boolean reach() {
        return isOn() && reach.getValue();
    }

    public boolean noAbort() {
        return isOn() && noAbort.getValue() && !mc.options.useKey.isPressed();
    }

    public boolean noReset() {
        return isOn() && noReset.getValue();
    }

    public boolean noDelay() {
        return isOn() && noDelay.getValue();
    }

    public boolean multiTask() {
        return isOn() && multiTask.getValue();
    }

    public boolean noEntityTrace() {
        if (isOff() || !noEntityTrace.getValue()) return false;

        if (onlyPickaxe.getValue()) {
            return mc.player.getMainHandStack().getItem() instanceof PickaxeItem || mc.player.isUsingItem() && !(mc.player.getMainHandStack().getItem() instanceof SwordItem);
        }
        return true;
    }

    public boolean ghostHand() {
        return isOn() && ghostHand.getValue() && !mc.options.useKey.isPressed() && !mc.options.sneakKey.isPressed();
    }
}
