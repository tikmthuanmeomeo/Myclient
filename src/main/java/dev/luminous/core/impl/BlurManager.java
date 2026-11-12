package dev.luminous.core.impl;

import dev.luminous.api.utils.Wrapper;
import net.minecraft.util.Identifier;
import satin.api.managed.ManagedShaderEffect;
import satin.api.managed.ShaderEffectManager;

public class BlurManager implements Wrapper {
    public static final ManagedShaderEffect BLUR = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/blurarea.json"));

    public void applyBlur(float radius, float startX, float startY, float width, float height) {
        float factor = (float) mc.getWindow().getScaleFactor() / 2;
        BLUR.setUniformValue("Radius", radius);
        BLUR.setUniformValue("BlurXY", startX * factor, (float) mc.getWindow().getHeight() / 2 - (startY + height) * factor);
        BLUR.setUniformValue("BlurCoord", width * factor, height * factor);
        BLUR.render(mc.getRenderTickCounter().getTickDelta(true));
    }
}