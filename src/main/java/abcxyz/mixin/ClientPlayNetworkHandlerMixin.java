package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.module.combat.Velocity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
    private void abcxyz$velocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // the packet is first read on the network thread and then handled again on the main thread: only act on the second pass
        if (mc.player == null || !mc.isOnThread() || AbcxyzClient.modules == null) return;
        if (packet.getEntityId() != mc.player.getId()) return;
        Velocity velocity = AbcxyzClient.modules.get(Velocity.class);
        if (velocity == null || !velocity.isEnabled()) return;
        ci.cancel();
        double h = velocity.horizontal.d() / 100.0;
        double v = velocity.vertical.d() / 100.0;
        mc.player.setVelocity(packet.getVelocityX() * h, packet.getVelocityY() * v, packet.getVelocityZ() * h);
    }
}
