package net.vanguard.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.vanguard.culling.EntityCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(
            method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vanguard$reuseDecimatedState(Entity entity, float partialTicks,
                                              CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState cached = EntityCuller.get().reuseState(entity, partialTicks);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(
            method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN")
    )
    private void vanguard$cacheExtractedState(Entity entity, float partialTicks,
                                              CallbackInfoReturnable<EntityRenderState> cir) {
        EntityCuller.get().storeState(entity, cir.getReturnValue());
    }

    @Inject(
            method = "finalizeRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vanguard$skipPeripheralShadows(Entity entity, EntityRenderState state, CallbackInfo ci) {
        if (EntityCuller.get().shouldSkipShadows(entity)) {
            state.shadowRadius = 0.0F;
            state.shadowPieces.clear();
            ci.cancel();
        }
    }
}
