package satin.api.managed.uniform;

import java.util.function.IntSupplier;

public interface SamplerUniformV2 extends SamplerUniform {
    void set(IntSupplier textureSupplier);
}
