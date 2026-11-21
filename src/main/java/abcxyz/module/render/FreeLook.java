package abcxyz.module.render;

import abcxyz.core.Category;
import abcxyz.core.KeySetting;
import abcxyz.core.Module;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * While you hold the key the mouse only turns the camera: your player keeps looking (and walking) the same way.
 * Let go and the view snaps back. The camera is turned in CameraMixin, the mouse is caught in EntityMixin.
 */
public class FreeLook extends Module {
    private final KeySetting holdKey = add(new KeySetting("HoldKey", GLFW.GLFW_KEY_LEFT_ALT));

    private boolean was = false;
    private float yaw, pitch;

    public FreeLook() {
        super("FreeLook", "Hold a key to look around without turning your player", Category.Render);
        holdKey.label = "Hold key";
    }

    @Override
    public void onDisable() {
        was = false;
    }

    /** True while the key is held (the camera angles start from the player's the moment it is pressed). */
    public boolean active() {
        boolean now = isEnabled() && mc.player != null && mc.currentScreen == null
                && holdKey.key() >= 0 && InputUtil.isKeyPressed(mc.getWindow().getHandle(), holdKey.key());
        if (now && !was) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
        }
        was = now;
        return now;
    }

    public void look(double cursorDeltaX, double cursorDeltaY) {
        yaw += (float) cursorDeltaX * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) cursorDeltaY * 0.15f, -90.0f, 90.0f);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }
}
