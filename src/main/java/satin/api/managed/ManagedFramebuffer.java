package satin.api.managed;

import net.minecraft.client.gl.Framebuffer;

public interface ManagedFramebuffer {
    Framebuffer getFramebuffer();

    void beginWrite(boolean updateViewport);

    void draw();

    void draw(int width, int height, boolean disableBlend);

    /**
     * Clears the content of this framebuffer.
     */
    void clear();

    void clear(boolean swallowErrors);
}
