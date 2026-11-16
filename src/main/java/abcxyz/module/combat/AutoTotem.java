package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Keeps a Totem of Undying in your offhand and (optionally) in a hotbar slot you choose with the slider.
 * When a totem is missing and one is in your inventory, it opens the inventory, moves the totem and closes it again.
 */
public class AutoTotem extends Module {
    private final NumSetting health = add(new NumSetting("Health", 20, 1, 36, 1));
    private final NumSetting delay = add(new NumSetting("Delay", 100, 0, 1000, 10));
    private final BoolSetting openInventory = add(new BoolSetting("OpenInventory", true));
    private final BoolSetting mainHand = add(new BoolSetting("MainHand", true));
    private final NumSetting slot = add(new NumSetting("Slot", 9, 1, 9, 1));

    private long lastAction = 0L;
    private boolean openedByUs = false;

    public AutoTotem() {
        super("AutoTotem", "Moves totems into your offhand and a hotbar slot", Category.Combat);
    }

    @Override
    public String info() {
        return mainHand.on() ? "slot " + slot.i() : null;
    }

    @Override
    public void onDisable() {
        closeIfOurs();
    }

    private void closeIfOurs() {
        if (openedByUs && mc.currentScreen instanceof InventoryScreen) mc.setScreen(null);
        openedByUs = false;
    }

    /** Container slot id of a totem in the main inventory (9..35), or in the hotbar when allowed; -1 if none. */
    private int findTotem(boolean allowHotbar, int skipHotbar) {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        if (allowHotbar) {
            for (int i = 0; i < 9; i++) {
                if (i == skipHotbar) continue;
                if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i + 36;
            }
        }
        return -1;
    }

    @Override
    public void onTick() {
        if (mc.currentScreen == null) openedByUs = false;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;

        int hotbarSlot = slot.i() - 1;
        boolean needOffhand = !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= health.d();
        boolean needMain = mainHand.on() && !mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TOTEM_OF_UNDYING);

        int offhandSource = needOffhand ? findTotem(true, mainHand.on() ? hotbarSlot : -1) : -1;
        int mainSource = needMain ? findTotem(false, -1) : -1;
        if (offhandSource == -1 && mainSource == -1) {
            // nothing (more) to do: put the inventory away if we opened it
            if (openedByUs && System.currentTimeMillis() - lastAction > Math.max(100, delay.d())) closeIfOurs();
            return;
        }

        long now = System.currentTimeMillis();
        if (openInventory.on() && mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            openedByUs = true;
            lastAction = now;
            return;
        }
        if (now - lastAction < delay.d()) return;

        int syncId = mc.player.playerScreenHandler.syncId;
        if (offhandSource != -1) {
            // button 40 swaps the clicked slot with the offhand
            mc.interactionManager.clickSlot(syncId, offhandSource, 40, SlotActionType.SWAP, mc.player);
        } else {
            // button 0..8 swaps the clicked slot with that hotbar slot
            mc.interactionManager.clickSlot(syncId, mainSource, hotbarSlot, SlotActionType.SWAP, mc.player);
        }
        lastAction = now;
    }
}
