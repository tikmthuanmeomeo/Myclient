package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Remembers which item belongs in each hotbar slot and moves items back after a regear / pickup.
 * Turn it on while your hotbar is arranged, or press "Save" later to take a new snapshot.
 */
public class HotbarSaver extends Module {
    public static HotbarSaver INSTANCE;

    private final SliderSetting delay = add(new SliderSetting("Delay", 150, 0, 1000, 10).setSuffix("ms"));
    private final BooleanSetting onlyInventory = add(new BooleanSetting("OnlyInInventory", false));
    private final BooleanSetting save = add(new BooleanSetting("Save", false).injectTask(this::saveTask));

    private final Item[] layout = new Item[9];
    private final Timer timer = new Timer();

    public HotbarSaver() {
        super("HotbarSaver", "Keeps items in their saved hotbar slots", Category.Player);
        INSTANCE = this;
    }

    private void saveTask() {
        if (save.getValue()) {
            save.setValueWithoutTask(false);
            if (!nullCheck()) {
                snapshot();
                sendMessage("§aHotbar saved");
            }
        }
    }

    private void snapshot() {
        for (int i = 0; i < 9; i++) {
            layout[i] = mc.player.getInventory().getStack(i).getItem();
        }
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        snapshot();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck() || !timer.passedMs(delay.getValue())) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return;
        if (onlyInventory.getValue() && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;

        for (int hot = 0; hot < 9; hot++) {
            Item want = layout[hot];
            if (want == null || want == Items.AIR) continue;
            if (mc.player.getInventory().getStack(hot).getItem() == want) continue;

            int from = findSource(want);
            if (from == -1) continue;
            // player screen handler: main inventory 9..35, hotbar 36..44; SWAP with button = hotbar index
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, from, hot, SlotActionType.SWAP, mc.player);
            timer.reset();
            return;
        }
    }

    /** Finds the item in the main inventory first, then in a hotbar slot that is not already correct. */
    private int findSource(Item want) {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == want) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (layout[i] == want) continue;
            if (mc.player.getInventory().getStack(i).getItem() == want) return i + 36;
        }
        return -1;
    }
}
