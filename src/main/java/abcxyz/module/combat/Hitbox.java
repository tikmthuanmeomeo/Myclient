package abcxyz.module.combat;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Makes other entities easier to target by enlarging the box used for aiming (the work is done in EntityMixin).
 * This only changes what your own client treats as "under the crosshair".
 */
public class Hitbox extends Module {
    public final NumSetting size = add(new NumSetting("Size", 0.3, 0.0, 1.5, 0.05));
    public final BoolSetting playersOnly = add(new BoolSetting("PlayersOnly", true));

    public Hitbox() {
        super("Hitbox", "Enlarges the hitbox of other entities for aiming", Category.Combat);
    }

    @Override
    public String info() {
        return String.format("%.2f", size.d());
    }

    public boolean applies(Entity entity) {
        if (entity == mc.player) return false;
        if (playersOnly.on()) return entity instanceof PlayerEntity;
        return entity instanceof LivingEntity;
    }
}
