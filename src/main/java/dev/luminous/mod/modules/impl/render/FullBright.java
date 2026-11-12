package dev.luminous.mod.modules.impl.render;

import dev.luminous.mod.modules.Module;

/**
 * Maximum brightness. The effect itself is applied in MixinLightmapTextureManager / MixinBackgroundRenderer.
 */
public class FullBright extends Module {
    public static FullBright INSTANCE;

    public FullBright() {
        super("FullBright", "Makes everything fully bright", Category.Render);
        INSTANCE = this;
    }
}
