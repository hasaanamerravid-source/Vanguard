package net.vanguard.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
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

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Shadow
    private Vec3 cameraPos;

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void vanguard$cullOccludedBlockEntity(BlockEntity blockEntity, float partialTicks,
                                                   ModelFeatureRenderer.CrumblingOverlay breakProgress,
                                                   boolean globallyRendered,
                                                   CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (OcclusionCuller.get().isBlockEntityOccluded(blockEntity, globallyRendered, this.cameraPos)) {
            EntityCuller.get().onBlockEntityCulled();
            cir.setReturnValue(null);
        }
    }
}
