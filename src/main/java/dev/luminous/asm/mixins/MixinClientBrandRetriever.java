package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.client.ClientSpoof;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class MixinClientBrandRetriever {
    @Inject(method = "getClientModName", at = @At("RETURN"), cancellable = true)
    private static void spoofBrand(CallbackInfoReturnable<String> cir) {
        ClientSpoof spoof = ClientSpoof.INSTANCE;
        if (spoof != null && spoof.isOn()) {
            cir.setReturnValue(spoof.brand());
        }
    }
}
