package abcxyz.module.combat;

import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

/**
 * Keeps a totem in your offhand and in one hotbar slot, the way a player would do it, in 5 safe steps:
 * tap the hotbar totem to the hand, open the inventory, move a totem to the offhand, move one to the hotbar slot,
 * close the inventory. It only works while you have 3 or more totems (so the last two stay where they are).
 * <p>
 * Ported from ArrowAddon (MIT License, (c) Nhân-Tài (ArrowAddon)), see THIRD_PARTY_NOTICES/ArrowAddon-LICENSE.txt.
 */
public class AutoTotem extends Module {
    private enum Stage {
        Idle, Step1_Tap, Step2_Open, Step3_Offhand, Step4_Hotbar, Step5_Close
    }

    private final NumSetting targetHotbarSlot = add(new NumSetting("HotbarSlot", 1, 1, 9, 1));
    private Stage stage = Stage.Idle;

    public AutoTotem() {
        super("AutoTotem", "Refills a totem in your offhand and a hotbar slot, in 5 safe steps", Category.Combat);
        targetHotbarSlot.label = "Hotbar slot (1-9)";
    }

    @Override
    public void onEnable() {
        stage = Stage.Idle;
    }

    @Override
    public void onDisable() {
        stage = Stage.Idle;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (countAllTotems() <= 2) {
            if (stage != Stage.Idle && mc.currentScreen instanceof InventoryScreen) mc.player.closeHandledScreen();
            stage = Stage.Idle;
            return;
        }
        boolean offhandEmpty = !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        int targetIdx = targetHotbarSlot.i() - 1;
        boolean hotbarNeedsRefill = !mc.player.getInventory().getStack(targetIdx).isOf(Items.TOTEM_OF_UNDYING);
        switch (stage) {
            case Idle -> {
                if (offhandEmpty || hotbarNeedsRefill) stage = Stage.Step1_Tap;
            }
            case Step1_Tap -> {
                int hotbarTotem = findHotbarTotem();
                if (offhandEmpty && hotbarTotem != -1) tapHotbarSlot(hotbarTotem);
                stage = Stage.Step2_Open;
            }
            case Step2_Open -> {
                if (mc.currentScreen == null && hasSpareTotem()) {
                    mc.setScreen(new InventoryScreen(mc.player));
                    stage = Stage.Step3_Offhand;
                } else if (mc.currentScreen instanceof InventoryScreen) {
                    stage = Stage.Step3_Offhand;
                } else {
                    stage = Stage.Idle;
                }
            }
            case Step3_Offhand -> {
                if (offhandEmpty && hasSpareTotem()) replenish(true);
                stage = Stage.Step4_Hotbar;
            }
            case Step4_Hotbar -> {
                if (hotbarNeedsRefill && hasSpareTotem()) replenish(false);
                stage = Stage.Step5_Close;
            }
            case Step5_Close -> {
                if (mc.currentScreen instanceof InventoryScreen) mc.player.closeHandledScreen();
                stage = Stage.Idle;
            }
        }
    }

    private void replenish(boolean offhand) {
        int totemSlot = findTotemInInventoryOnly();
        if (totemSlot == -1) return;
        moveCursorOver(totemSlot);
        swap(totemSlot, offhand ? 40 : targetHotbarSlot.i() - 1);
    }

    private void tapHotbarSlot(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    /** Puts the mouse pointer over the slot, so the inventory looks used by hand. */
    private void moveCursorOver(int slotId) {
        Screen screen = mc.currentScreen;
        if (!(screen instanceof InventoryScreen inv)) return;
        Slot slot = inv.getScreenHandler().slots.get(slotId);
        if (slot == null) return;
        int guiLeft = (inv.width - 176) / 2;
        int guiTop = (inv.height - 166) / 2;
        double mouseX = guiLeft + slot.x + 10;
        double mouseY = guiTop + slot.y + 10;
        long handle = mc.getWindow().getHandle();
        double scale = (double) mc.getWindow().getWidth() / (double) inv.width;
        GLFW.glfwSetCursorPos(handle, mouseX * scale, mouseY * scale);
    }

    private void swap(int slotId, int button) {
        Screen screen = mc.currentScreen;
        if (screen instanceof InventoryScreen inv) {
            mc.interactionManager.clickSlot(inv.getScreenHandler().syncId, slotId, button, SlotActionType.SWAP, mc.player);
        }
    }

    private int countAllTotems() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) count++;
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) count++;
        return count;
    }

    private int findHotbarTotem() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private int findTotemInInventoryOnly() {
        if (mc.player.playerScreenHandler == null) return -1;
        for (Slot slot : mc.player.playerScreenHandler.slots) {
            if (slot.id >= 9 && slot.id <= 35 && slot.getStack().isOf(Items.TOTEM_OF_UNDYING)) return slot.id;
        }
        return -1;
    }

    private boolean hasSpareTotem() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return true;
        }
        return false;
    }
}
