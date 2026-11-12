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
 * Same idea as HotbarSaver but for the 27 main inventory slots.
 * Save: remember the inventory. Clear: forget it. Turn the module on to move items back.
 */
public class InvSaver extends Module {
    public static InvSaver INSTANCE;
    public static final int SLOTS = 27;

    private final BooleanSetting save = add(new BooleanSetting("Save", false).injectTask(this::saveTask));
    private final BooleanSetting clear = add(new BooleanSetting("Clear", false).injectTask(this::clearTask));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    /** Comma separated item ids for inventory slots 9..35. Hidden from the GUI, stored in the config. */
    private final StringSetting saved = add(new StringSetting("Saved", "", () -> false));

    private final Item[] layout = new Item[SLOTS];
    private String parsedFrom = null;
    private final Timer timer = new Timer();

    public InvSaver() {
        super("InvSaver", "Saves your inventory and puts the items back", Category.Player);
        INSTANCE = this;
    }

    private void parse() {
        String raw = saved.getValue();
        if (raw == null) raw = "";
        if (raw.equals(parsedFrom)) return;
        parsedFrom = raw;
        String[] parts = raw.split(",", -1);
        for (int i = 0; i < SLOTS; i++) {
            Item item = Items.AIR;
            if (i < parts.length && !parts[i].isBlank()) {
                Identifier id = Identifier.tryParse(parts[i].trim());
                if (id != null) item = Registries.ITEM.get(id);
            }
            layout[i] = item;
        }
    }

    /** Saved item for inventory slot 0..26 (inventory index 9 + slot). */
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
        for (int i = 0; i < SLOTS; i++) {
            if (i > 0) sb.append(',');
            Item item = mc.player.getInventory().getStack(9 + i).getItem();
            sb.append(item == Items.AIR ? "" : Registries.ITEM.getId(item).toString());
        }
        saved.setValue(sb.toString());
        parse();
        sendMessage("§aInventory saved");
    }

    private void clearTask() {
        if (!clear.getValue()) return;
        clear.setValueWithoutTask(false);
        saved.setValue("");
        parse();
        if (!nullCheck()) sendMessage("§cSaved inventory cleared");
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        if (!hasSaved()) {
            sendMessage("§cNo inventory saved. Press Save first.");
            disable();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck() || !timer.passedMs(150)) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen) && !(mc.currentScreen instanceof ClickGuiScreen)) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        parse();

        for (int i = 0; i < SLOTS; i++) {
            Item want = layout[i];
            if (want == null || want == Items.AIR) continue;
            int target = 9 + i;
            if (mc.player.getInventory().getStack(target).getItem() == want) continue;
            int from = findSource(want);
            if (from == -1) continue;
            moveItem(from, target);
            timer.reset();
            return;
        }
        // nothing left that can be fixed
        if (autoDisable.getValue()) disable();
    }

    /** Main inventory slot (9..35) holding the wanted item that is not already in its own saved slot. */
    private int findSource(Item want) {
        for (int i = 9; i < 36; i++) {
            Item saved = layout[i - 9];
            if (saved == want) continue;
            if (mc.player.getInventory().getStack(i).getItem() == want) return i;
        }
        return -1;
    }

    /** Pick up the source stack, drop it on the target and put what was there back into the source. */
    private void moveItem(int from, int to) {
        int id = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(id, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(id, to, 0, SlotActionType.PICKUP, mc.player);
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(id, from, 0, SlotActionType.PICKUP, mc.player);
        }
    }
}
