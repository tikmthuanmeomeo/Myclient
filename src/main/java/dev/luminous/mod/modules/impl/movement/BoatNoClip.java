package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Vec3d;

/**
 * The boat you ride ignores block collisions, so it can be steered through walls.
 * Shift (sneak) no longer gets you out of the boat: it moves the boat down instead, and the
 * DismountKey (L by default) is what gets you off. Use it together with BoatFly to go up as well.
 */
public class BoatNoClip extends Module {
    public static BoatNoClip INSTANCE;

    private final BooleanSetting noGravity = add(new BooleanSetting("NoGravity", true));
    private final SliderSetting downSpeed = add(new SliderSetting("DownSpeed", 0.5, 0.1, 3.0, 0.1));
    private final BindSetting dismountKey = add(new BindSetting("DismountKey", 76)); // 76 = L

    private BoatEntity lastBoat = null;
    private int dismountTicks = 0;
    private boolean sending = false;

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

    /** The server dismounts you when it hears that shift is pressed, so that packet is not sent while riding. */
    @EventListener
    public void onSend(PacketEvent.Send event) {
        if (sending || nullCheck()) return;
        if (event.getPacket() instanceof ClientCommandC2SPacket packet
                && packet.getMode() == ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY
                && mc.player.getVehicle() instanceof BoatEntity) {
            event.cancel();
        }
    }

    private void sendSneak(boolean press) {
        sending = true;
        try {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player,
                    press ? ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY : ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
        } finally {
            sending = false;
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) return;
        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof BoatEntity boat)) {
            release();
            dismountTicks = 0;
            return;
        }
        if (lastBoat != boat) {
            release();
            lastBoat = boat;
        }
        boat.noClip = true;
        if (noGravity.getValue()) boat.setNoGravity(true);

        // shift = down (when BoatFly is on it handles the vertical movement itself)
        if (mc.options.sneakKey.isPressed() && (BoatFly.INSTANCE == null || !BoatFly.INSTANCE.isOn())) {
            Vec3d v = boat.getVelocity();
            boat.setVelocity(v.x, -downSpeed.getValue(), v.z);
        }

        // DismountKey: hold "shift" on the server for a few ticks so it lets you off the boat
        if (dismountTicks > 0) {
            dismountTicks--;
            if (dismountTicks == 0) sendSneak(false);
        } else if (mc.currentScreen == null && dismountKey.isPressed()) {
            sendSneak(true);
            dismountTicks = 3;
        }
    }
}
