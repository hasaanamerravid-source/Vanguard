package net.vanguard.mixin.neoforge;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.vanguard.culling.EntityCuller;
import net.vanguard.culling.OcclusionCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge extends the vanilla block entity extraction entry point with an
 * overload that also receives a culling frustum. The vanilla four-argument
 * method delegates to this overload, so hooking the five-argument method
 * covers both call paths.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherNeoForgeMixin {
    @Shadow
    private Vec3 cameraPos;

    @Inject(
            method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;F" +
                    "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z" +
                    "Lnet/minecraft/client/renderer/culling/Frustum;)" +
                    "Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vanguard$cullOccludedBlockEntity(BlockEntity blockEntity, float partialTicks,
                                                   ModelFeatureRenderer.CrumblingOverlay breakProgress,
                                                   boolean globallyRendered, Frustum frustum,
                                                   CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (OcclusionCuller.get().isBlockEntityOccluded(blockEntity, globallyRendered, this.cameraPos)) {
            EntityCuller.get().onBlockEntityCulled();
            cir.setReturnValue(null);
        }
    }
}
