package abcxyz.module.combat;

import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/** Keeps a Totem of Undying in your offhand. */
public class AutoTotem extends Module {
    private final NumSetting health = add(new NumSetting("Health", 20, 1, 36, 1));
    private final NumSetting delay = add(new NumSetting("Delay", 150, 0, 1000, 10));

    private long last = 0L;

    public AutoTotem() {
        super("AutoTotem", "Moves a totem into your offhand", Category.Combat);
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (mc.player.getHealth() + mc.player.getAbsorptionAmount() > health.d()) return;
        long now = System.currentTimeMillis();
        if (now - last < delay.d()) return;

        int slot = -1;
        for (int i = 9; i < 36 && slot == -1; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) slot = i;
        }
        for (int i = 0; i < 9 && slot == -1; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) slot = i + 36;
        }
        if (slot == -1) return;
        // button 40 swaps the clicked slot with the offhand
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
        last = now;
    }
}
