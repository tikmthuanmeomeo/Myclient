package abcxyz.module.misc;

import abcxyz.core.Category;
import abcxyz.core.ModeSetting;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

/**
 * Removes the 4 tick wait between two right clicks while you hold the use key, so blocks go down much faster.
 * The cooldown is changed in MinecraftClientMixin.
 */
public class FastUse extends Module {
    private final NumSetting delay = add(new NumSetting("Delay", 0, 0, 4, 1));
    private final ModeSetting items = add(new ModeSetting("Items", "Blocks", "Blocks", "All"));

    public FastUse() {
        super("FastUse", "Place blocks (or use items) faster while holding right click", Category.Misc);
        delay.label = "Delay (ticks)";
    }

    @Override
    public String info() {
        return delay.i() + "t";
    }

    /** Ticks to wait between two uses (vanilla is 4). */
    public int cooldown() {
        return delay.i();
    }

    public boolean applies() {
        if (mc.player == null) return false;
        if (items.is("All")) return true;
        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();
        return main.getItem() instanceof BlockItem || (main.isEmpty() && off.getItem() instanceof BlockItem);
    }
}
