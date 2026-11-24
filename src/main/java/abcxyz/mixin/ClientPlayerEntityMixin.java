package abcxyz.mixin;

import abcxyz.core.SilentRotation;
import abcxyz.crystalaura.ServerRotation;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    /** Runs once per tick, right before the movement packet (position + rotation) is built and sent. */
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void abcxyz$beforeMovement(CallbackInfo ci) {
        ServerRotation.beforeMovement((ClientPlayerEntity) (Object) this);
        SilentRotation.apply((ClientPlayerEntity) (Object) this);
    }

    /** The movement packet is out: put the real rotation back. */
    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void abcxyz$afterMovement(CallbackInfo ci) {
        SilentRotation.restore((ClientPlayerEntity) (Object) this);
    }
}
