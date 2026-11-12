package dev.luminous.api.interfaces;

import net.minecraft.client.gl.Framebuffer;

public interface IShaderEffectHook {
    void alienClient$addHook(String name, Framebuffer buffer);
}