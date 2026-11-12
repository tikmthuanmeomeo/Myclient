package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.gui.ClickGuiScreen;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

/**
 * Save: remember the current hotbar. Clear: forget it. Turn the module on to move the saved
 * items back into their slots; AutoDisable switches it off again once the hotbar is restored.
 */
public class HotbarSaver extends Module {
    public static HotbarSaver INSTANCE;

    private final BooleanSetting save = add(new BooleanSetting("Save", false).injectTask(this::saveTask));
    private final BooleanSetting clear = add(new BooleanSetting("Clear", false).injectTask(this::clearTask));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    /** Saved hotbar as a comma separated list of item ids. Hidden from the GUI, stored in the config. */
    private final StringSetting saved = add(new StringSetting("Saved", "", () -> false));

    private final Item[] layout = new Item[9];
    private String parsedFrom = null;
    private final Timer timer = new Timer();

    public HotbarSaver() {
        super("HotbarSaver", "Saves your hotbar and puts the items back", Category.Player);
        INSTANCE = this;
    }

    private void parse() {
        String raw = saved.getValue();
        if (raw == null) raw = "";
        if (raw.equals(parsedFrom)) return;
        parsedFrom = raw;
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < 9; i++) {
            Item item = Items.AIR;
            if (i < parts.length && !parts[i].isBlank()) {
                Identifier id = Identifier.tryParse(parts[i].trim());
                if (id != null) item = Registries.ITEM.get(id);
            }
            layout[i] = item;
        }
    }

    /** Item saved for hotbar slot 0..8 (Items.AIR when empty). */
    public ItemStack getSavedStack(int slot) {
        parse();
        Item item = layout[slot];
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public boolean hasSaved() {
        parse();
        for (Item item : layout) {
            if (item != null && item != Items.AIR) return true;
        }
        return false;
    }

    private void saveTask() {
        if (!save.getValue()) return;
        save.setValueWithoutTask(false);
        if (nullCheck()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            if (i > 0) sb.append(',');
            Item item = mc.player.getInventory().getStack(i).getItem();
            sb.append(item == Items.AIR ? "" : Registries.ITEM.getId(item).toString());
        }
        saved.setValue(sb.toString());
        parse();
        sendMessage("§aHotbar saved");
    }

    private void clearTask() {
        if (!clear.getValue()) return;
        clear.setValueWithoutTask(false);
        saved.setValue("");
        parse();
        if (!nullCheck()) sendMessage("§cSaved hotbar cleared");
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        if (!hasSaved()) {
            sendMessage("§cNo hotbar saved. Press Save first.");
            disable();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck() || !timer.passedMs(120)) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen) && !(mc.currentScreen instanceof ClickGuiScreen)) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        parse();

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
        // nothing left that can be fixed
        if (autoDisable.getValue()) disable();
    }

    /** Main inventory first, then a hotbar slot that is not already correct. */
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
