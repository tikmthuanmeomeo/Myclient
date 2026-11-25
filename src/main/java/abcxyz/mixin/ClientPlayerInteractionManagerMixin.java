package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.misc.SpeedMine;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    /** SpeedMine: the click on a block starts the packet mining instead of the normal digging. */
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void abcxyz$speedMine(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (AbcxyzClient.modules == null) return;
        SpeedMine speedMine = AbcxyzClient.modules.get(SpeedMine.class);
        if (speedMine != null && speedMine.isEnabled() && speedMine.start(pos, direction)) cir.setReturnValue(true);
    }
}
