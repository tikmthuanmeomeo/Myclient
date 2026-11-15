package abcxyz.module.movement;

import abcxyz.core.Category;
import abcxyz.core.Module;

public class Sprint extends Module {
    public Sprint() {
        super("Sprint", "Always sprints when you move forward", Category.Movement);
    }

    @Override
    public void onTick() {
        if (mc.player.isSneaking() || mc.player.horizontalCollision || mc.player.isUsingItem()) return;
        if (mc.player.getHungerManager().getFoodLevel() <= 6) return;
        if (mc.options.forwardKey.isPressed()) mc.player.setSprinting(true);
    }
}
