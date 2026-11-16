package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Keeps a Totem of Undying in your offhand and in a hotbar slot you choose.
 * Settings: "Offhand" (right click: Health, Delay), "MainHand" (right click: Slot, Delay) and "Gui".
 * It reacts every rendered frame. With "Gui" off it opens the inventory, swaps and closes it again within the
 * same frame; with "Gui" on it never shows the inventory and sends the swaps silently.
 */
public class AutoTotem extends Module {
    private final NumSetting health = add(new NumSetting("Health", 20, 1, 36, 1));
    private final NumSetting offhandDelay = add(new NumSetting("OffhandDelay", 40, 0, 500, 10));
    private final BoolSetting offhand = add(new BoolSetting("Offhand", true)).with(health, offhandDelay);

    private final NumSetting slot = add(new NumSetting("Slot", 9, 1, 9, 1));
    private final NumSetting mainHandDelay = add(new NumSetting("MainHandDelay", 40, 0, 500, 10));
    private final BoolSetting mainHand = add(new BoolSetting("MainHand", true)).with(slot, mainHandDelay);

    private final BoolSetting gui = add(new BoolSetting("Gui", false));

    private long lastOffhand = 0L;
    private long lastMainHand = 0L;

    public AutoTotem() {
        super("AutoTotem", "Moves totems into your offhand and a hotbar slot", Category.Combat);
        offhandDelay.label = "Delay";
        mainHandDelay.label = "Delay";
    }

    @Override
    public String info() {
        return mainHand.on() ? "slot " + slot.i() : null;
    }

    /** Container slot id of a totem in the main inventory (9..35) or hotbar (36..44); -1 if none. */
    private int findTotem(boolean allowHotbar, int skipHotbar, int skipSlot) {
        for (int i = 9; i < 36; i++) {
            if (i == skipSlot) continue;
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        if (allowHotbar) {
            for (int i = 0; i < 9; i++) {
                if (i == skipHotbar || i + 36 == skipSlot) continue;
                if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i + 36;
            }
        }
        return -1;
    }

    @Override
    public void onFrame() {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (mc.interactionManager == null || !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        long now = System.currentTimeMillis();

        int hotbarSlot = slot.i() - 1;
        boolean needOffhand = offhand.on() && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= health.d();
        boolean needMain = mainHand.on() && !mc.player.getInventory().getStack(hotbarSlot).isOf(Items.TOTEM_OF_UNDYING);

        int offhandSource = needOffhand ? findTotem(true, mainHand.on() ? hotbarSlot : -1, -1) : -1;
        int mainSource = needMain ? findTotem(false, -1, offhandSource) : -1;
        boolean doOffhand = offhandSource != -1 && now - lastOffhand >= offhandDelay.d();
        boolean doMain = mainSource != -1 && now - lastMainHand >= mainHandDelay.d();
        if (!doOffhand && !doMain) return;

        boolean showInventory = !gui.on() && mc.currentScreen == null;
        if (showInventory) mc.setScreen(new InventoryScreen(mc.player));

        int syncId = mc.player.playerScreenHandler.syncId;
        if (doOffhand) {
            // button 40 swaps the clicked slot with the offhand
            mc.interactionManager.clickSlot(syncId, offhandSource, 40, SlotActionType.SWAP, mc.player);
            lastOffhand = now;
        }
        if (doMain) {
            // button 0..8 swaps the clicked slot with that hotbar slot
            mc.interactionManager.clickSlot(syncId, mainSource, hotbarSlot, SlotActionType.SWAP, mc.player);
            lastMainHand = now;
        }

        if (showInventory) mc.setScreen(null);
    }
}
