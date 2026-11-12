package satin.api.managed;

import net.minecraft.client.gl.ShaderProgram;
import satin.api.managed.uniform.UniformFinder;

public interface ManagedCoreShader extends UniformFinder {
    ShaderProgram getProgram();

    void release();
}