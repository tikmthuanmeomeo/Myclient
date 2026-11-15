package abcxyz.module.combat;

import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;

/** Scales the knockback you receive (the work is done in ClientPlayNetworkHandlerMixin). */
public class Velocity extends Module {
    public final NumSetting horizontal = add(new NumSetting("Horizontal", 0, 0, 100, 5));
    public final NumSetting vertical = add(new NumSetting("Vertical", 0, 0, 100, 5));

    public Velocity() {
        super("Velocity", "Reduces the knockback you take", Category.Combat);
    }

    @Override
    public String info() {
        return horizontal.i() + "% " + vertical.i() + "%";
    }
}
