package net.vanguard.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import net.vanguard.culling.EntityCuller;
import net.vanguard.culling.OcclusionCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void vanguard$beginFrame(DeltaTracker deltaTracker, Camera camera, float partialTicks, CallbackInfo ci) {
        EntityCuller.get().beginFrame(camera);
    }

    @ModifyVariable(method = "isEntityVisible", at = @At("HEAD"), index = 2, argsOnly = true)
    private Frustum vanguard$predictiveFrustum(Frustum frustum) {
        return EntityCuller.get().frustumFor(frustum);
    }

    @Inject(method = "isEntityVisible", at = @At("HEAD"))
    private void vanguard$countCandidate(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                         float partialTicks, long chunkFadeDuration,
                                         CallbackInfoReturnable<Boolean> cir) {
        EntityCuller.get().onCandidate();
    }

    @Inject(method = "isEntityVisible", at = @At("RETURN"), cancellable = true)
    private void vanguard$cullOccludedEntity(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                             float partialTicks, long chunkFadeDuration,
                                             CallbackInfoReturnable<Boolean> cir) {
        EntityCuller culler = EntityCuller.get();
        if (!cir.getReturnValueZ()) {
            culler.onRejected();
            return;
        }
        if (OcclusionCuller.get().isEntityOccluded(entity, camX, camY, camZ)) {
            culler.onOccluded();
            cir.setReturnValue(false);
            return;
        }
        culler.onRendered();
    }
}
