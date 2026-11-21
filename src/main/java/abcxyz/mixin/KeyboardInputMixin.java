package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.render.Freecam;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    /** Freecam: the movement keys fly the camera, so the player itself gets no input. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void abcxyz$freecamInput(CallbackInfo ci) {
        if (AbcxyzClient.modules == null) return;
        Freecam freecam = AbcxyzClient.modules.get(Freecam.class);
        if (freecam == null || !freecam.isActive()) return;
        this.playerInput = PlayerInput.DEFAULT;
        this.movementForward = 0.0f;
        this.movementSideways = 0.0f;
    }
}
