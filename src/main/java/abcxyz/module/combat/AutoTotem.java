package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

/**
 * Keeps a Totem of Undying in your offhand and in a hotbar slot you choose.
 * Settings: "Offhand" (right click: Health, Delay), "MainHand" (right click: Slot, Health, Delay), "Freeze"
 * (right click: Ticks) and "Gui".
 * MainHand "Health": once your health drops to that value the selected hotbar slot is switched to the totem slot
 * (once per drop, so you can still scroll away afterwards; it re-arms when you heal above the value).
 * Freeze: every time a totem is moved into the offhand and/or the hotbar slot you stand still for the set number
 * of ticks (movement keys are released, sprint and horizontal speed are cancelled), so the swaps go through cleanly.
 * It reacts every rendered frame. With "Gui" off it opens the inventory, swaps and closes it again within the
 * same frame; with "Gui" on it never shows the inventory and sends the swaps silently.
 */
public class AutoTotem extends Module {
    private final NumSetting health = add(new NumSetting("Health", 20, 1, 36, 1));
    private final NumSetting offhandDelay = add(new NumSetting("OffhandDelay", 40, 0, 500, 10));
    private final BoolSetting offhand = add(new BoolSetting("Offhand", true)).with(health, offhandDelay);

    private final NumSetting slot = add(new NumSetting("Slot", 9, 1, 9, 1));
    private final NumSetting mainHandHealth = add(new NumSetting("MainHandHealth", 10, 1, 36, 1));
    private final NumSetting mainHandDelay = add(new NumSetting("MainHandDelay", 40, 0, 500, 10));
    private final BoolSetting mainHand = add(new BoolSetting("MainHand", true)).with(slot, mainHandHealth, mainHandDelay);

    private final NumSetting freezeTicks = add(new NumSetting("FreezeTicks", 1, 1, 5, 1));
    private final BoolSetting freeze = add(new BoolSetting("Freeze", true)).with(freezeTicks);

    private final BoolSetting gui = add(new BoolSetting("Gui", false));

    private long lastOffhand = 0L;
    private long lastMainHand = 0L;
    private int freezeLeft = 0;
    private boolean lowSwitched = false;

    public AutoTotem() {
        super("AutoTotem", "Moves totems into your offhand and a hotbar slot", Category.Combat);
        offhandDelay.label = "Delay";
        mainHandDelay.label = "Delay";
        mainHandHealth.label = "Health";
        freezeTicks.label = "Ticks";
    }

    @Override
    public void onDisable() {
        if (freezeLeft > 0) {
            freezeLeft = 0;
            KeyBinding.updatePressedStates();
        }
        lowSwitched = false;
    }

    /** Releases the movement keys and cancels sprint / horizontal speed. */
    private void holdStill() {
        GameOptions o = mc.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sprintKey.setPressed(false);
        mc.player.setSprinting(false);
        Vec3d v = mc.player.getVelocity();
        mc.player.setVelocity(0.0, v.y, 0.0);
    }

    @Override
    public void onTick() {
        if (freezeLeft <= 0) return;
        freezeLeft--;
        if (freezeLeft > 0) holdStill();
        else KeyBinding.updatePressedStates(); // give the keys you are still holding back
    }

    @Override
    public String info() {
        return mainHand.on() ? "slot " + slot.i() : null;
    }

    /** Container slot id of a totem in the main inventory (9..35) or hotbar (36..44); -1 if none. */
    private double mainHealthValue() {
        return mainHandHealth.d();
    }

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
        if (freezeLeft > 0) holdStill();

        if (mainHand.on()) {
            int target = slot.i() - 1;
            float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
            if (hp > mainHealthValue()) {
                lowSwitched = false;
            } else if (!lowSwitched && mc.player.getInventory().getStack(target).isOf(Items.TOTEM_OF_UNDYING)) {
                mc.player.getInventory().selectedSlot = target;
                lowSwitched = true;
            }
        }

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

        if (freeze.on()) {
            freezeLeft = freezeTicks.i();
            holdStill();
        }

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
