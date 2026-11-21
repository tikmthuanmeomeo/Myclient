package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;

/**
 * Turns off things you may not want to see. The work is done in mixins:
 * Fog (BackgroundRendererMixin), Items (EntityRenderDispatcherMixin), HurtCam (GameRendererMixin) and
 * In wall (InGameOverlayRendererMixin).
 */
public class NoRender extends Module {
    private final BoolSetting fog = add(new BoolSetting("Fog", true));
    private final BoolSetting items = add(new BoolSetting("Items", true));
    private final BoolSetting hurtCam = add(new BoolSetting("HurtCam", true));
    private final BoolSetting inWall = add(new BoolSetting("InWall", true));

    public NoRender() {
        super("NoRender", "Hide fog, dropped items, the hurt camera shake and the in-wall overlay", Category.Render);
        fog.label = "Fog";
        items.label = "Items";
        hurtCam.label = "Hurt cam";
        inWall.label = "In wall";
    }

    public boolean noFog() {
        return isEnabled() && fog.on();
    }

    /** Dropped items on the ground. */
    public boolean noItems() {
        return isEnabled() && items.on();
    }

    public boolean noHurtCam() {
        return isEnabled() && hurtCam.on();
    }

    /** The dark overlay you get inside a block, for example after an ender pearl lands in a wall. */
    public boolean noInWall() {
        return isEnabled() && inWall.on();
    }
}
