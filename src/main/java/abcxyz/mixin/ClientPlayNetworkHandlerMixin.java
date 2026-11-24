package abcxyz.mixin;

import abcxyz.AbcxyzClient;
import abcxyz.core.TickRate;
import abcxyz.module.combat.AnchorAura;
import abcxyz.module.combat.CrystalAura;
import abcxyz.module.combat.Velocity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
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

    /** The server sends the world time every 20 ticks: the time between two of them gives the TPS (HudEdit). */
    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"))
    private void abcxyz$tps(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        if (MinecraftClient.getInstance().isOnThread()) TickRate.onTimeUpdate();
    }

    /** A block changed on the server (modules that watch blocks, like the chunk finders, hear about it). TAIL: only the main-thread pass gets here. */
    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void abcxyz$blockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        if (AbcxyzClient.modules != null) AbcxyzClient.modules.blockUpdate(packet.getPos(), packet.getState());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void abcxyz$chunkDelta(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        if (AbcxyzClient.modules == null) return;
        packet.visitUpdates((pos, state) -> AbcxyzClient.modules.blockUpdate(pos.toImmutable(), state));
    }

    /** Crystal aura: it needs to hear about crystals appearing, damage and removed entities. TAIL: main-thread pass only. */
    private void abcxyz$crystalPacket(net.minecraft.network.packet.Packet<?> packet) {
        if (AbcxyzClient.modules == null) return;
        CrystalAura aura = AbcxyzClient.modules.get(CrystalAura.class);
        if (aura != null && aura.isEnabled()) aura.onPacket(packet);
        AnchorAura anchor = AbcxyzClient.modules.get(AnchorAura.class);
        if (anchor != null && anchor.isEnabled()) anchor.onPacket(packet);
    }

    @Inject(method = "onEntitySpawn", at = @At("TAIL"))
    private void abcxyz$spawn(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        abcxyz$crystalPacket(packet);
    }

    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void abcxyz$damage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        abcxyz$crystalPacket(packet);
    }

    @Inject(method = "onEntitiesDestroy", at = @At("TAIL"))
    private void abcxyz$destroy(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        abcxyz$crystalPacket(packet);
    }
}
