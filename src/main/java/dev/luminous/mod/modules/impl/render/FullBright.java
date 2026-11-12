package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Maximum brightness. Gives the player a client-side Night Vision effect (no mixin needed) and
 * keeps it topped up; the effect is removed again when the module is turned off.
 */
public class FullBright extends Module {
    public static FullBright INSTANCE;

    private boolean applied = false;

    public FullBright() {
        super("FullBright", "Makes everything fully bright", Category.Render);
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        StatusEffectInstance current = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current == null || (applied && current.getDuration() < 400)) {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0, false, false, false));
            applied = true;
        }
    }

    @Override
    public void onDisable() {
        if (applied && mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        applied = false;
    }
}
