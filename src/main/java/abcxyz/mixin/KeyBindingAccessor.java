package abcxyz.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    /** Clicks of this key the game has not handled yet. */
    @Accessor("timesPressed")
    int abcxyz$timesPressed();
}
