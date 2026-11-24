package abcxyz.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {
    /** The sprint state the server was last told about. */
    @Accessor("lastSprinting")
    boolean abcxyz$wasSprinting();
}
