package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium's own gathering of the block entities in view (it replaces the game's, in the main pass and Iris's shadow pass):
 * each one's render state timed and remembered to it, as the game's is (DESIGN §6). Applies only with Sodium, and leaves
 * that part unmeasured where it cannot.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
abstract class SodiumFrameMixin {
    @WrapOperation(require = 0, method = "extractBlockEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"))
    private BlockEntityRenderState culprit$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float partial,
                                                       ModelFeatureRenderer.CrumblingOverlay breaking, boolean global,
                                                       Operation<BlockEntityRenderState> original) {
        if (!FrameProbe.measuring()) {
            return original.call(dispatcher, blockEntity, partial, breaking, global);
        }
        long start = FrameProbe.begin();
        BlockEntityRenderState state = null;
        try {
            state = original.call(dispatcher, blockEntity, partial, breaking, global);
            return state;
        } finally {
            long ns = FrameProbe.end(start);
            if (state != null) {
                FrameProbe.blockEntity(blockEntity, ns);
                FrameProbe.stateOf(state, blockEntity);
            } else {
                FrameProbe.phase(FramePhase.EXTRACT, ns);
            }
        }
    }
}
