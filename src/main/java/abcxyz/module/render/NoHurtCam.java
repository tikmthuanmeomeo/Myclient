package abcxyz.module.render;

import abcxyz.core.Category;
import abcxyz.core.Module;

/** Removes the camera shake when you take damage. The work is done in GameRendererMixin. */
public class NoHurtCam extends Module {
    public NoHurtCam() {
        super("NoHurtCam", "Removes the screen shake when you get hurt", Category.Render);
    }
}
