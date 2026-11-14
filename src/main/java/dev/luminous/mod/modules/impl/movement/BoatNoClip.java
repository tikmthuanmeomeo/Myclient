package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;

/**
 * The boat you ride ignores block collisions, so it can be steered through walls.
 * Use it together with BoatFly to move up and down; on its own the boat just stays at its height.
 */
public class BoatNoClip extends Module {
    public static BoatNoClip INSTANCE;

    private final BooleanSetting noGravity = add(new BooleanSetting("NoGravity", true));

    private BoatEntity lastBoat = null;

    public BoatNoClip() {
        super("BoatNoClip", "Lets the boat you ride pass through blocks", Category.Movement);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        release();
    }

    private void release() {
        if (lastBoat != null) {
            lastBoat.noClip = false;
            lastBoat.setNoGravity(false);
            lastBoat = null;
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof BoatEntity boat)) {
            release();
            return;
        }
        if (lastBoat != boat) {
            release();
            lastBoat = boat;
        }
        boat.noClip = true;
        if (noGravity.getValue()) boat.setNoGravity(true);
    }
}
