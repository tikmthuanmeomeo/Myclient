package abcxyz.module.render;

import abcxyz.core.Category;
import abcxyz.core.Module;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Gives you a client-side Night Vision effect. */
public class FullBright extends Module {
    private boolean applied = false;

    public FullBright() {
        super("FullBright", "Makes everything bright", Category.Render);
    }

    @Override
    public void onTick() {
        StatusEffectInstance current = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current == null || (applied && current.getDuration() < 400)) {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
            applied = true;
        }
    }

    @Override
    public void onDisable() {
        if (applied && mc.player != null) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        applied = false;
    }
}
