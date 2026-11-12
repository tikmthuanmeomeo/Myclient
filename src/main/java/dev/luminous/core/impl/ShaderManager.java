package dev.luminous.core.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.api.interfaces.IShaderEffectHook;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.impl.render.ShaderModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL30C;
import satin.api.managed.ManagedShaderEffect;
import satin.api.managed.ShaderEffectManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ShaderManager implements Wrapper {

    static final Timer timer = new Timer();
    private final static List<RenderTask> tasks = new ArrayList<>();
    public static ManagedShaderEffect DEFAULT_OUTLINE;
    public static ManagedShaderEffect PULSE_OUTLINE;
    public static ManagedShaderEffect SMOKE_OUTLINE;
    public static ManagedShaderEffect GRADIENT_OUTLINE;
    public static ManagedShaderEffect SNOW_OUTLINE;
    public static ManagedShaderEffect FLOW_OUTLINE;
    public static ManagedShaderEffect RAINBOW_OUTLINE;
    public static ManagedShaderEffect DEFAULT;
    public static ManagedShaderEffect PULSE;
    public static ManagedShaderEffect SMOKE;
    public static ManagedShaderEffect GRADIENT;
    public static ManagedShaderEffect SNOW;
    public static ManagedShaderEffect FLOW;
    public static ManagedShaderEffect RAINBOW;
    public float time = 0;
    private MyFramebuffer shaderBuffer;

    public void renderShader(Runnable runnable, Shader mode) {
        tasks.add(new RenderTask(runnable, mode));
    }

    public void renderShaders() {
        tasks.forEach(t -> applyShader(t.task(), t.shader()));
        tasks.clear();
    }

    public void applyShader(Runnable runnable, Shader mode) {
        if (fullNullCheck()) return;
        RenderSystem.assertOnRenderThreadOrInit();
        Framebuffer MCBuffer = MinecraftClient.getInstance().getFramebuffer();
        if (shaderBuffer.textureWidth != MCBuffer.textureWidth || shaderBuffer.textureHeight != MCBuffer.textureHeight)
            shaderBuffer.resize(MCBuffer.textureWidth, MCBuffer.textureHeight, false);
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, shaderBuffer.fbo);
        shaderBuffer.beginWrite(true);
        runnable.run();
        shaderBuffer.endWrite();
        GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, MCBuffer.fbo);
        MCBuffer.beginWrite(false);
        ManagedShaderEffect shader = getShader(mode);
        PostEffectProcessor effect = shader.getShaderEffect();

        if (effect != null)
            ((IShaderEffectHook) effect).alienClient$addHook("bufIn", shaderBuffer);

        Framebuffer outBuffer = shader.getShaderEffect().getSecondaryTarget("bufOut");
        setupShader(mode, shader);
        shaderBuffer.clear(false);
        MCBuffer.beginWrite(false);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
        RenderSystem.backupProjectionMatrix();
        outBuffer.draw(outBuffer.textureWidth, outBuffer.textureHeight, false);
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.disableBlend();
    }

    public ManagedShaderEffect getShader(@NotNull Shader mode) {
        return switch (mode) {
            case Gradient -> GRADIENT;
            case Smoke -> SMOKE;
            case Pulse -> PULSE;
            case Snow -> SNOW;
            case Flow -> FLOW;
            case Rainbow -> RAINBOW;
            default -> DEFAULT;
        };
    }

    public ManagedShaderEffect getShaderOutline(@NotNull Shader mode) {
        return switch (mode) {
            case Gradient -> GRADIENT_OUTLINE;
            case Smoke -> SMOKE_OUTLINE;
            case Snow -> SNOW_OUTLINE;
            case Flow -> FLOW_OUTLINE;
            case Rainbow -> RAINBOW_OUTLINE;
            case Pulse -> PULSE_OUTLINE;
            default -> DEFAULT_OUTLINE;
        };
    }

    public void setupShader(Shader shader, ManagedShaderEffect effect) {
        ShaderModule module = ShaderModule.INSTANCE;
        Color color = module.fill.getValue();
        time = timer.getMs() / 5f * module.speed.getValueFloat() * 0.004f;
        //time += (float) shaderModule.speed.getValue() * 0.002f;
        if (shader == Shader.Rainbow) {
            effect.setUniformValue("alpha2", (color.getAlpha() / 255f));

            effect.setUniformValue("radius", module.radius.getValueFloat());
            effect.setUniformValue("quality", module.smoothness.getValueFloat());
            effect.setUniformValue("divider", module.divider.getValueFloat());
            effect.setUniformValue("maxSample", module.maxSample.getValueFloat());

            effect.setUniformValue("resolution", (float) mc.getWindow().getScaledWidth(), (float) mc.getWindow().getScaledHeight());
            effect.setUniformValue("time", time);
            effect.render(mc.getRenderTickCounter().getTickDelta(true));

        } else if (shader == Shader.Gradient) {
            effect.setUniformValue("alpha2", (color.getAlpha() / 255f));
            effect.setUniformValue("rgb", module.smoke1.getValue().getRed() / 255f, module.smoke1.getValue().getGreen() / 255f, module.smoke1.getValue().getBlue() / 255f);
            effect.setUniformValue("rgb1", module.smoke2.getValue().getRed() / 255f, module.smoke2.getValue().getGreen() / 255f, module.smoke2.getValue().getBlue() / 255f);
            effect.setUniformValue("rgb2", module.smoke3.getValue().getRed() / 255f, module.smoke3.getValue().getGreen() / 255f, module.smoke3.getValue().getBlue() / 255f);
            effect.setUniformValue("rgb3", module.smoke4.getValue().getRed() / 255f, module.smoke4.getValue().getGreen() / 255f, module.smoke4.getValue().getBlue() / 255f);
            effect.setUniformValue("step", module.step.getValueFloat() * 300f);
            effect.setUniformValue("radius", module.radius.getValueFloat());
            effect.setUniformValue("quality", module.smoothness.getValueFloat());
            effect.setUniformValue("divider", module.divider.getValueFloat());
            effect.setUniformValue("maxSample", module.maxSample.getValueFloat());

            effect.setUniformValue("resolution", (float) mc.getWindow().getScaledWidth(), (float) mc.getWindow().getScaledHeight());
            effect.setUniformValue("time", time * 300);
            effect.render(mc.getRenderTickCounter().getTickDelta(true));

        } else if (shader == Shader.Smoke) {
            effect.setUniformValue("alpha1", (color.getAlpha() / 255f));

            effect.setUniformValue("radius", module.radius.getValueFloat());
            effect.setUniformValue("quality", module.smoothness.getValueFloat());
            effect.setUniformValue("divider", module.divider.getValueFloat());
            effect.setUniformValue("maxSample", module.maxSample.getValueFloat());

            effect.setUniformValue("first", module.smoke1.getValue().getRed() / 255f, module.smoke1.getValue().getGreen() / 255f, module.smoke1.getValue().getBlue() / 255f, module.smoke1.getValue().getAlpha() / 255f);
            effect.setUniformValue("second", module.smoke2.getValue().getRed() / 255f, module.smoke2.getValue().getGreen() / 255f, module.smoke2.getValue().getBlue() / 255f);
            effect.setUniformValue("third", module.smoke3.getValue().getRed() / 255f, module.smoke3.getValue().getGreen() / 255f, module.smoke3.getValue().getBlue() / 255f);
            effect.setUniformValue("oct", (int) module.octaves.getValue());
            effect.setUniformValue("resolution", (float) mc.getWindow().getScaledWidth(), (float) mc.getWindow().getScaledHeight());
            effect.setUniformValue("time", time);
            effect.render(mc.getRenderTickCounter().getTickDelta(true));

        } else if (shader == Shader.Solid) {
            effect.setUniformValue("mixFactor", color.getAlpha() / 255f);
            effect.setUniformValue("minAlpha", module.alpha.getValueFloat() / 255f);
            effect.setUniformValue("radius", module.radius.getValueFloat());
            effect.setUniformValue("quality", module.smoothness.getValueFloat());
            effect.setUniformValue("divider", module.divider.getValueFloat());
            effect.setUniformValue("maxSample", module.maxSample.getValueFloat());
            effect.setUniformValue("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);
            effect.setUniformValue("resolution", (float) mc.getWindow().getScaledWidth(), (float) mc.getWindow().getScaledHeight());
            effect.render(mc.getRenderTickCounter().getTickDelta(true));
        } else if (shader == Shader.Pulse) {
            effect.setUniformValue("mixFactor", color.getAlpha() / 255f);
            effect.setUniformValue("minAlpha", module.alpha.getValueFloat() / 255f);
            effect.setUniformValue("radius", module.radius.getValueFloat());
            effect.setUniformValue("quality", module.smoothness.getValueFloat());
            effect.setUniformValue("divider", module.divider.getValueFloat());
            effect.setUniformValue("maxSample", module.maxSample.getValueFloat());
            effect.setUniformValue("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);
            Color color2 = module.pulse.getValue();
            effect.setUniformValue("color2", color2.getRed() / 255f, color2.getGreen() / 255f, color2.getBlue() / 255f);
            effect.setUniformValue("time", time);
            effect.setUniformValue("size", module.pulseSpeed.getValueFloat());
            effect.setUniformValue("resolution", (float) mc.getWindow().getScaledWidth(), (float) mc.getWindow().getScaledHeight());
            effect.render(mc.getRenderTickCounter().getTickDelta(true));
        } else if (shader == Shader.Snow) {
            effect.setUniformValue("color", color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
            effect.setUniformValue("radius", module.radius.getValueFloat());
            effect.setUniformValue("quality", module.smoothness.getValueFloat());
            effect.setUniformValue("divider", module.divider.getValueFloat());
            effect.setUniformValue("maxSample", module.maxSample.getValueFloat());
            effect.setUniformValue("resolution", (float) mc.getWindow().getScaledWidth(), (float) mc.getWindow().getScaledHeight());
            effect.setUniformValue("time", time);
            effect.render(mc.getRenderTickCounter().getTickDelta(true));

        } else if (shader == Shader.Flow) {
            effect.setUniformValue("mixFactor", color.getAlpha() / 255f);
            effect.setUniformValue("radius", module.radius.getValueFloat());
            effect.setUniformValue("quality", module.smoothness.getValueFloat());
            effect.setUniformValue("divider", module.divider.getValueFloat());
            effect.setUniformValue("maxSample", module.maxSample.getValueFloat());
            effect.setUniformValue("resolution", (float) mc.getWindow().getScaledWidth(), (float) mc.getWindow().getScaledHeight());
            effect.setUniformValue("time", time);
            effect.render(mc.getRenderTickCounter().getTickDelta(true));
        }
    }

    public void reloadShaders() {
        DEFAULT = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/outline.json"));
        SMOKE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/smoke.json"));
        GRADIENT = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/gradient.json"));
        SNOW = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/snow.json"));
        FLOW = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/flow.json"));
        RAINBOW = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/rainbow.json"));
        PULSE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/pulse.json"));

        DEFAULT_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/outline.json"), managedShaderEffect -> {
            PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
            if (effect == null) return;

            ((IShaderEffectHook) effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
            ((IShaderEffectHook) effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
        });

        PULSE_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/pulse.json"), managedShaderEffect -> {
            PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
            if (effect == null) return;

            ((IShaderEffectHook) effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
            ((IShaderEffectHook) effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
        });

        SMOKE_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/smoke.json"), managedShaderEffect -> {
            PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
            if (effect == null) return;

            ((IShaderEffectHook) effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
            ((IShaderEffectHook) effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
        });

        GRADIENT_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/gradient.json"), managedShaderEffect -> {
            PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
            if (effect == null) return;

            ((IShaderEffectHook) effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
            ((IShaderEffectHook) effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
        });

        SNOW_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/snow.json"), managedShaderEffect -> {
            PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
            if (effect == null) return;

            ((IShaderEffectHook) effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
            ((IShaderEffectHook) effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
        });

        FLOW_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/flow.json"), managedShaderEffect -> {
            PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
            if (effect == null) return;

            ((IShaderEffectHook) effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
            ((IShaderEffectHook) effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
        });
        RAINBOW_OUTLINE = ShaderEffectManager.getInstance().manage(Identifier.of("shaders/post/rainbow.json"), managedShaderEffect -> {
            PostEffectProcessor effect = managedShaderEffect.getShaderEffect();
            if (effect == null) return;

            ((IShaderEffectHook) effect).alienClient$addHook("bufIn", mc.worldRenderer.getEntityOutlinesFramebuffer());
            ((IShaderEffectHook) effect).alienClient$addHook("bufOut", mc.worldRenderer.getEntityOutlinesFramebuffer());
        });
    }

    public boolean fullNullCheck() {
        if (GRADIENT == null || SMOKE == null || DEFAULT == null || FLOW == null || RAINBOW == null || PULSE == null || PULSE_OUTLINE == null
                || GRADIENT_OUTLINE == null || SMOKE_OUTLINE == null || DEFAULT_OUTLINE == null || FLOW_OUTLINE == null || RAINBOW_OUTLINE == null
                || shaderBuffer == null) {
            if (mc.getFramebuffer() == null) return true;
            shaderBuffer = new MyFramebuffer(mc.getFramebuffer().textureWidth, mc.getFramebuffer().textureHeight);
            reloadShaders();
            return true;
        }

        return false;
    }

    public enum Shader {
        Solid,
        Pulse,
        Smoke,
        Gradient,
        Snow,
        Flow,
        Rainbow
    }

    public static class MyFramebuffer extends Framebuffer {
        public MyFramebuffer(int width, int height) {
            super(false);
            RenderSystem.assertOnRenderThreadOrInit();
            resize(width, height, true);
            setClearColor(0f, 0f, 0f, 0f);
        }
    }

    public record RenderTask(Runnable task, Shader shader) {
    }
}
