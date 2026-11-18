package abcxyz.module.misc;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Buys Totems of Undying from the server's GUI shop until your inventory is full, then turns itself off.
 * /shop open gear -> click the totem -> click the confirm button -> repeat.
 *
 * - The shop window is never shown (HideGui): MinecraftClientMixin drops the screen but the window itself stays
 *   open, so clicks still work and you can keep playing/moving while it buys.
 * - If the server closes the shop after a purchase it is opened again with the command; if the window stays open
 *   the next totem is bought right away (ReopenShop forces the command after every purchase).
 * - Nothing blocks the game thread: it is a state machine run every frame with millisecond timers, so a step
 *   happens as soon as the server answered instead of waiting for the next tick.
 * - It stops (and closes the window) when the main inventory (slots 0-35) is full, when the server says you don't
 *   have enough money, when the shop does not open (3 tries), or when no totem was bought for 10 seconds.
 *   The reason is written in the chat.
 */
public class AutoShop extends Module {
    private static final String COMMAND = "shop open gear";
    private static final long OPEN_TIMEOUT_MS = 3000;
    private static final int OPEN_ATTEMPTS = 3;
    private static final long STALL_MS = 10000;
    private static final List<String> NO_MONEY = List.of("not enough", "insufficient", "can't afford", "cannot afford",
            "don't have enough", "do not have enough", "không đủ tiền", "khong du tien", "không đủ xu", "thiếu tiền");
    private static final List<String> CONFIRM_WORDS = List.of("confirm", "accept", "purchase", "xác nhận", "xac nhan",
            "đồng ý", "dong y");
    private static final List<Item> GREEN_ITEMS = List.of(Items.LIME_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE,
            Items.LIME_STAINED_GLASS, Items.GREEN_STAINED_GLASS, Items.LIME_CONCRETE, Items.GREEN_CONCRETE,
            Items.LIME_WOOL, Items.GREEN_WOOL, Items.LIME_TERRACOTTA, Items.GREEN_TERRACOTTA, Items.EMERALD_BLOCK,
            Items.EMERALD, Items.LIME_DYE, Items.GREEN_DYE);

    private final NumSetting clickDelay = add(new NumSetting("ClickDelay", 50, 0, 1000, 10));
    private final BoolSetting hideGui = add(new BoolSetting("HideGui", true));
    private final BoolSetting reopenShop = add(new BoolSetting("ReopenShop", false));
    private final NumSetting confirmSlot = add(new NumSetting("ConfirmSlot", -1, -1, 53, 1));

    private enum State {OPEN_SHOP, WAIT_SHOP, SELECT_ITEM, WAIT_CONFIRM, CONFIRM, WAIT_RESULT}

    private State state = State.OPEN_SHOP;
    private long nextAt = 0L;
    private long stateSince = 0L;
    private int openAttempts = 0;
    private int syncIdAtClick = -1;
    private int hashAtClick = 0;
    private int totemsAtClick = 0;
    private int lastTotems = 0;
    private long lastProgress = 0L;

    public AutoShop() {
        super("AutoShop", "Buys totems from the /shop until your inventory is full", Category.Misc);
        clickDelay.label = "Click delay (ms)";
        hideGui.label = "Hide GUI";
        reopenShop.label = "Reopen every time";
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

    /** True while the shop window must not be shown on screen (used by MinecraftClientMixin). */
    public boolean hidesGui() {
        return isEnabled() && hideGui.on();
    }

    @Override
    public void onEnable() {
        state = State.OPEN_SHOP;
        nextAt = 0L;
        stateSince = System.currentTimeMillis();
        openAttempts = 0;
        lastTotems = mc.player == null ? 0 : countTotems();
        lastProgress = System.currentTimeMillis();
    }

    private void stop(String reason) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("\u00a7e[AutoShop] \u00a7f" + reason), false);
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

    /** The open chest-like window (it does not need to be on screen), or null. */
    private GenericContainerScreenHandler container() {
        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) return null;
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) return handler;
        return null;
    }

    private static int containerSize(GenericContainerScreenHandler handler) {
        return Math.min(handler.getRows() * 9, handler.slots.size());
    }

    private boolean hasContents(GenericContainerScreenHandler handler) {
        for (int i = 0; i < containerSize(handler); i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) return true;
        }
        return false;
    }

    private int contentHash(GenericContainerScreenHandler handler) {
        int hash = 1;
        for (int i = 0; i < containerSize(handler); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            hash = 31 * hash + (stack.isEmpty() ? 0 : stack.getItem().hashCode() * 17 + stack.getCount());
            hash = 31 * hash + (stack.isEmpty() ? 0 : stack.getName().getString().hashCode());
        }
        return hash;
    }

    private int findTotemSlot(GenericContainerScreenHandler handler) {
        for (int i = 0; i < containerSize(handler); i++) {
            if (handler.getSlot(i).getStack().isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    /** The confirm button: the manual slot, or the first item named "confirm"..., or the first green item. */
    private int findConfirmSlot(GenericContainerScreenHandler handler) {
        int size = containerSize(handler);
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

    private void go(State next, long now) {
        state = next;
        stateSince = now;
        nextAt = now + clickDelay.i();
    }

    // ---------------------------------------------------------------- state machine (every frame)

    @Override
    public void onFrame() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();

        if (inventoryFull()) {
            stop("inventory is full, done");
            return;
        }
        int totems = countTotems();
        if (totems > lastTotems) lastProgress = now;
        lastTotems = totems;
        if (now - lastProgress > STALL_MS) {
            stop("no totem was bought for 10 seconds (state " + state.name() + ")");
            return;
        }
        if (now < nextAt) return;

        GenericContainerScreenHandler handler = container();
        switch (state) {
            case OPEN_SHOP -> {
                if (handler != null && findTotemSlot(handler) != -1) { // the shop is already open
                    go(State.SELECT_ITEM, now);
                    return;
                }
                if (handler != null) { // some other window: close it first
                    mc.player.closeHandledScreen();
                    nextAt = now + Math.max(50, clickDelay.i());
                    return;
                }
                mc.player.networkHandler.sendChatCommand(COMMAND);
                state = State.WAIT_SHOP;
                stateSince = now;
            }
            case WAIT_SHOP -> {
                if (handler != null && hasContents(handler)) {
                    openAttempts = 0;
                    go(State.SELECT_ITEM, now);
                } else if (now - stateSince > OPEN_TIMEOUT_MS) {
                    if (++openAttempts >= OPEN_ATTEMPTS) {
                        stop("the shop did not open");
                    } else { // maybe a command cooldown: try again
                        state = State.OPEN_SHOP;
                        stateSince = now;
                        nextAt = now + 500;
                    }
                }
            }
            case SELECT_ITEM -> {
                if (handler == null) {
                    go(State.OPEN_SHOP, now);
                    return;
                }
                int slot = findTotemSlot(handler);
                if (slot == -1) {
                    if (now - stateSince > OPEN_TIMEOUT_MS) stop("no totem found in the shop window");
                    return;
                }
                syncIdAtClick = handler.syncId;
                hashAtClick = contentHash(handler);
                totemsAtClick = totems;
                click(handler, slot);
                go(State.WAIT_CONFIRM, now);
            }
            case WAIT_CONFIRM -> {
                if (handler == null || totems > totemsAtClick) { // closed or bought straight away
                    afterPurchase(now);
                } else if (handler.syncId != syncIdAtClick || contentHash(handler) != hashAtClick) {
                    go(State.CONFIRM, now);
                } else if (now - stateSince > 2000) {
                    afterPurchase(now); // nothing happened: try again
                }
            }
            case CONFIRM -> {
                if (handler == null || totems > totemsAtClick) {
                    afterPurchase(now);
                    return;
                }
                int slot = findConfirmSlot(handler);
                if (slot == -1) {
                    if (now - stateSince > OPEN_TIMEOUT_MS) stop("confirm button not found (set Confirm slot)");
                    return;
                }
                syncIdAtClick = handler.syncId;
                hashAtClick = contentHash(handler);
                click(handler, slot);
                go(State.WAIT_RESULT, now);
            }
            case WAIT_RESULT -> {
                if (handler == null || totems > totemsAtClick || handler.syncId != syncIdAtClick
                        || contentHash(handler) != hashAtClick || now - stateSince > 1000) {
                    afterPurchase(now);
                }
            }
        }
    }

    private void afterPurchase(long now) {
        GenericContainerScreenHandler handler = container();
        if (reopenShop.on() && handler != null) {
            mc.player.closeHandledScreen();
            handler = null;
        }
        // the window is still open and shows the shop: buy the next one right away, else open the shop again
        go(handler != null && findTotemSlot(handler) != -1 ? State.SELECT_ITEM : State.OPEN_SHOP, now);
    }
}
