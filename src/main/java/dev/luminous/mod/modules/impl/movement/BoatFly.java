package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;

/**
 * Lets the boat you are sitting in fly. Move with the movement keys, jump to go up and the sprint key
 * to go down. Original implementation, modelled on the options of Meteor's BoatFly.
 */
public class BoatFly extends Module {
    public static BoatFly INSTANCE;

    private final SliderSetting speed = add(new SliderSetting("Speed", 1.0, 0.1, 5.0, 0.1));
    private final SliderSetting verticalSpeed = add(new SliderSetting("VerticalSpeed", 0.5, 0.1, 3.0, 0.1));
    private final SliderSetting glide = add(new SliderSetting("Glide", 0.0, 0.0, 0.5, 0.01));
    private final BooleanSetting followYaw = add(new BooleanSetting("FollowYaw", true));
    private final BooleanSetting cancelServer = add(new BooleanSetting("CancelServer", true));

    private BoatEntity lastBoat = null;

    public BoatFly() {
        super("BoatFly", "Makes the boat you ride fly", Category.Movement);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        release();
    }

    private void release() {
        if (lastBoat != null) {
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
        boat.setNoGravity(true);

        float yaw = mc.player.getYaw();
        if (followYaw.getValue()) boat.setYaw(yaw);

        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double strafe = (mc.options.leftKey.isPressed() ? 1 : 0) - (mc.options.rightKey.isPressed() ? 1 : 0);
        double rad = Math.toRadians(yaw);
        double x = 0;
        double z = 0;
        if (forward != 0 || strafe != 0) {
            double len = Math.sqrt(forward * forward + strafe * strafe);
            forward /= len;
            strafe /= len;
            x = (-Math.sin(rad) * forward + Math.cos(rad) * strafe) * speed.getValue();
            z = (Math.cos(rad) * forward + Math.sin(rad) * strafe) * speed.getValue();
        }

        double y;
        if (mc.options.jumpKey.isPressed()) {
            y = verticalSpeed.getValue();
        } else if (mc.options.sprintKey.isPressed()) {
            y = -verticalSpeed.getValue();
        } else {
            y = -glide.getValue();
        }
        boat.setVelocity(x, y, z);
    }

    @EventListener
    public void onReceive(PacketEvent.Receive event) {
        if (!cancelServer.getValue() || nullCheck()) return;
        // the server keeps trying to put the boat back where it thinks it should be
        if (event.getPacket() instanceof VehicleMoveS2CPacket && mc.player.getVehicle() instanceof BoatEntity) {
            event.cancel();
        }
    }
}
