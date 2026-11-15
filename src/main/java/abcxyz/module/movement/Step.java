package abcxyz.module.movement;

import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

/** Lets you walk up blocks higher than one step. */
public class Step extends Module {
    private final NumSetting height = add(new NumSetting("Height", 1.0, 0.6, 2.5, 0.1));

    public Step() {
        super("Step", "Walk up higher blocks", Category.Movement);
    }

    @Override
    public String info() {
        return String.format("%.1f", height.d());
    }

    @Override
    public void onTick() {
        EntityAttributeInstance attribute = mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
        if (attribute != null && attribute.getBaseValue() != height.d()) attribute.setBaseValue(height.d());
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        EntityAttributeInstance attribute = mc.player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
        if (attribute != null) attribute.setBaseValue(0.6);
    }
}
