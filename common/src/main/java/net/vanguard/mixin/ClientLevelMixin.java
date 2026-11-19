package net.vanguard.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.vanguard.culling.EntityCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
    private void vanguard$cullPeripheralParticles(ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow,
                                                  double x, double y, double z, double xd, double yd, double zd,
                                                  CallbackInfo ci) {
        EntityCuller culler = EntityCuller.get();
        if (culler.shouldCullParticle(x, y, z, overrideLimiter, alwaysShow)) {
            culler.onParticleCulled();
            ci.cancel();
        }
    }
}
