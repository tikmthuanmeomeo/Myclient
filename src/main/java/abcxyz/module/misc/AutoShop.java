package abcxyz.module.misc;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Buys Totems of Undying from the server's GUI shop in a loop until your inventory is full.
 * /shop open gear -> click the totem -> click the confirm button -> repeat.
 * Everything is tick based (no sleeping). It turns itself off and closes the screen when the main inventory
 * (slots 0-35) has no empty slot left, when the server says you don't have enough money, when the shop does not
 * open within 3 seconds, or when purchases stop going through.
 */
public class AutoShop extends Module {
    private static final String COMMAND = "shop open gear";
    private static final int TIMEOUT_TICKS = 60; // 3 seconds
    private static final List<String> NO_MONEY = List.of("not enough", "insufficient", "can't afford", "cannot afford",
            "don't have enough", "do not have enough", "không đủ tiền", "khong du tien", "không đủ xu", "thiếu tiền");
    private static final List<String> CONFIRM_WORDS = List.of("confirm", "accept", "purchase", "xác nhận", "xac nhan",
            "đồng ý", "dong y");
    private static final List<Item> GREEN_ITEMS = List.of(Items.LIME_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE,
            Items.LIME_STAINED_GLASS, Items.GREEN_STAINED_GLASS, Items.LIME_CONCRETE, Items.GREEN_CONCRETE,
            Items.LIME_WOOL, Items.GREEN_WOOL, Items.LIME_TERRACOTTA, Items.GREEN_TERRACOTTA, Items.EMERALD_BLOCK,
            Items.EMERALD, Items.LIME_DYE, Items.GREEN_DYE);

    private final NumSetting clickDelay = add(new NumSetting("ClickDelay", 150, 50, 1000, 10));
    private final BoolSetting reopenShop = add(new BoolSetting("ReopenShop", true));
    private final NumSetting confirmSlot = add(new NumSetting("ConfirmSlot", -1, -1, 53, 1));

    private enum State {OPEN_SHOP, WAIT_SHOP, SELECT_ITEM, WAIT_CONFIRM, CONFIRM, WAIT_RESULT}

    private State state = State.OPEN_SHOP;
    private int wait = 0;
    private int timeout = 0;
    private int syncIdAtClick = -1;
    private int hashAtClick = 0;
    private int lastTotems = 0;
    private int failedPurchases = 0;

    public AutoShop() {
        super("AutoShop", "Buys totems from the /shop until your inventory is full", Category.Misc);
        clickDelay.label = "Click delay (ms)";
        reopenShop.label = "Reopen shop";
        confirmSlot.label = "Confirm slot";
        // messages from the server (system messages, and chat that is not signed by a player)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
            if (sender == null) onMessage(message.getString());
        });
    }

    @Override
    public String info() {
        return state.name();
    }

    @Override
    public void onEnable() {
        state = State.OPEN_SHOP;
        wait = 0;
        timeout = 0;
        failedPurchases = 0;
        lastTotems = mc.player == null ? 0 : countTotems();
    }

    @Override
    public void onDisable() {
        wait = 0;
    }

    private void stop(String reason) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("AutoShop: " + reason), true);
            mc.player.closeHandledScreen();
        }
        setEnabled(false, true);
    }

    private void onMessage(String text) {
        if (!isEnabled()) return;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String key : NO_MONEY) {
            if (lower.contains(key)) {
                stop("not enough money");
                return;
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private int delayTicks() {
        return Math.max(1, (int) Math.round(clickDelay.d() / 50.0));
    }

    private boolean inventoryFull() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private int countTotems() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        return count;
    }

    /** The open shop/confirm window, or null when no chest-like window is open. */
    private GenericContainerScreenHandler container() {
        if (!(mc.currentScreen instanceof GenericContainerScreen)) return null;
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) return handler;
        return null;
    }

    private static int containerSize(GenericContainerScreenHandler handler) {
        return handler.getRows() * 9;
    }

    private boolean hasContents(GenericContainerScreenHandler handler) {
        int size = Math.min(containerSize(handler), handler.slots.size());
        for (int i = 0; i < size; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) return true;
        }
        return false;
    }

    private int contentHash(GenericContainerScreenHandler handler) {
        int hash = 1;
        int size = Math.min(containerSize(handler), handler.slots.size());
        for (int i = 0; i < size; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            hash = 31 * hash + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 17 + stack.getCount());
            hash = 31 * hash + (stack.isEmpty() ? 0 : stack.getName().getString().hashCode());
        }
        return hash;
    }

    private int findTotemSlot(GenericContainerScreenHandler handler) {
        int size = Math.min(containerSize(handler), handler.slots.size());
        for (int i = 0; i < size; i++) {
            if (handler.getSlot(i).getStack().isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    /** The confirm button: the manual slot, or the first item named "confirm"..., or the first green item. */
    private int findConfirmSlot(GenericContainerScreenHandler handler) {
        int size = Math.min(containerSize(handler), handler.slots.size());
        int manual = confirmSlot.i();
        if (manual >= 0) {
            return manual < size && !handler.getSlot(manual).getStack().isEmpty() ? manual : -1;
        }
        for (int i = 0; i < size; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) continue;
            String name = stack.getName().getString().toLowerCase(Locale.ROOT);
            for (String word : CONFIRM_WORDS) {
                if (name.contains(word)) return i;
            }
        }
        for (int i = 0; i < size; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && GREEN_ITEMS.contains(stack.getItem())) return i;
        }
        return -1;
    }

    private void click(GenericContainerScreenHandler handler, int slot) {
        if (slot < 0 || slot >= handler.slots.size()) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }

    // ---------------------------------------------------------------- state machine

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (inventoryFull()) {
            stop("inventory is full");
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        switch (state) {
            case OPEN_SHOP -> {
                if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
                    mc.player.closeHandledScreen(); // close whatever is still open first
                    wait = 2;
                    return;
                }
                mc.player.networkHandler.sendChatCommand(COMMAND);
                state = State.WAIT_SHOP;
                timeout = 0;
            }
            case WAIT_SHOP -> {
                GenericContainerScreenHandler handler = container();
                if (handler != null && hasContents(handler)) {
                    state = State.SELECT_ITEM;
                    timeout = 0;
                    wait = delayTicks();
                } else if (++timeout > TIMEOUT_TICKS) {
                    stop("the shop did not open in 3 seconds");
                }
            }
            case SELECT_ITEM -> {
                GenericContainerScreenHandler handler = container();
                if (handler == null) { // the window is gone: open the shop again
                    state = State.OPEN_SHOP;
                    wait = delayTicks();
                    return;
                }
                int slot = findTotemSlot(handler);
                if (slot == -1) {
                    if (++timeout > TIMEOUT_TICKS) stop("no totem found in the shop window");
                    return;
                }
                syncIdAtClick = handler.syncId;
                hashAtClick = contentHash(handler);
                click(handler, slot);
                state = State.WAIT_CONFIRM;
                timeout = 0;
                wait = delayTicks();
            }
            case WAIT_CONFIRM -> {
                GenericContainerScreenHandler handler = container();
                if (handler == null) { // window closed by the click: it was a direct purchase
                    afterPurchase();
                    return;
                }
                if (handler.syncId != syncIdAtClick || contentHash(handler) != hashAtClick) {
                    state = State.CONFIRM;
                    timeout = 0;
                    wait = delayTicks();
                } else if (++timeout > TIMEOUT_TICKS) {
                    afterPurchase(); // nothing changed: there is no confirm window, the click was the purchase
                }
            }
            case CONFIRM -> {
                GenericContainerScreenHandler handler = container();
                if (handler == null) {
                    afterPurchase();
                    return;
                }
                int slot = findConfirmSlot(handler);
                if (slot == -1) {
                    if (++timeout > TIMEOUT_TICKS) stop("confirm button not found (set Confirm slot)");
                    return;
                }
                click(handler, slot);
                state = State.WAIT_RESULT;
                wait = delayTicks() + 2; // let the inventory update
            }
            case WAIT_RESULT -> afterPurchase();
        }
    }

    private void afterPurchase() {
        int totems = countTotems();
        if (totems > lastTotems) failedPurchases = 0;
        else failedPurchases++;
        lastTotems = totems;
        if (inventoryFull()) {
            stop("inventory is full");
            return;
        }
        if (failedPurchases >= 3) {
            stop("purchases are not going through");
            return;
        }
        if (reopenShop.on()) {
            if (container() != null) mc.player.closeHandledScreen();
            state = State.OPEN_SHOP;
        } else {
            state = State.SELECT_ITEM;
        }
        timeout = 0;
        wait = delayTicks();
    }
}
