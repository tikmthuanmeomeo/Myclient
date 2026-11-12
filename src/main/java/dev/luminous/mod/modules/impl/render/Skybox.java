package dev.luminous.mod.modules.impl.render;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.ColorSetting;

import java.awt.*;

/**
 * Custom sky and cloud colors. Applied in MixinClientWorld.
 */
public class Skybox extends Module {
    public static Skybox INSTANCE;

    public final ColorSetting sky = add(new ColorSetting("Sky", new Color(20, 35, 80)));
    public final ColorSetting cloud = add(new ColorSetting("Clouds", new Color(60, 70, 100)).injectBoolean(true));

    public Skybox() {
        super("Skybox", "Changes the color of the sky and clouds", Category.Render);
        INSTANCE = this;
    }
}
