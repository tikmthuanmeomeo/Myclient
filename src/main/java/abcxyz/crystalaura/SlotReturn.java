/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.crystalaura;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;

/** Takes a hotbar slot for the module and gives the previous one back when it is done. */
public final class SlotReturn {
    private int previous = -1;
    private int taken = -1;

    public SlotReturn(Object owner) {
    }

    public boolean take(ClientPlayerEntity player, int slot) {
        PlayerInventory inventory = player.getInventory();
        if (inventory.selectedSlot == slot && !this.holding()) {
            return true;
        }
        if (!PlayerInventory.isValidHotbarIndex(slot)) {
            return false;
        }
        if (!this.holding()) {
            this.previous = inventory.selectedSlot;
        }
        this.taken = slot;
        inventory.selectedSlot = slot;
        return true;
    }

    public boolean holding() {
        return this.taken != -1;
    }

    public void tick(MinecraftClient mc, boolean back) {
        if (!this.holding()) {
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (player == null) {
            this.finish(null, false);
        } else if (mc.currentScreen == null && !MeleeAttacks.actedThisTick((PlayerEntity)player)) {
            this.finish(player, back);
        }
    }

    public void drop(MinecraftClient mc, boolean back) {
        if (this.holding()) {
            this.finish(mc.player, back);
        }
    }

    private void finish(ClientPlayerEntity player, boolean back) {
        if (back && player != null && player.getInventory().selectedSlot == this.taken && this.previous != this.taken) {
            player.getInventory().selectedSlot = this.previous;
        }
        this.previous = -1;
        this.taken = -1;
    }
}
