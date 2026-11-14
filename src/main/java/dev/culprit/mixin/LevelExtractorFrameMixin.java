package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.FramePhase;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The first step of drawing the world, timed during a frame scan: each entity's and block entity's render state made
 * (charged to it, and the state remembered so its drawing is charged to it too), and the particles'.
 */
@Mixin(LevelExtractor.class)
abstract class LevelExtractorFrameMixin {
    @WrapMethod(require = 0, method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;")
    private EntityRenderState culprit$entity(Entity entity, float partial, Operation<EntityRenderState> original) {
        if (!FrameProbe.measuring()) {
            return original.call(entity, partial);
        }
        long start = FrameProbe.begin();
        EntityRenderState state = null;
        try {
            state = original.call(entity, partial);
            return state;
        } finally {
            FrameProbe.entity(entity, FrameProbe.end(start));
            FrameProbe.stateOf(state, entity);
        }
    }

    @WrapOperation(require = 0, method = "extractVisibleBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;ZLnet/minecraft/client/renderer/culling/Frustum;)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"))
    private BlockEntityRenderState culprit$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float partial,
                                                       ModelFeatureRenderer.CrumblingOverlay breaking, boolean global, Frustum frustum,
                                                       Operation<BlockEntityRenderState> original) {
        if (!FrameProbe.measuring()) {
            return original.call(dispatcher, blockEntity, partial, breaking, global, frustum);
        }
        long start = FrameProbe.begin();
        BlockEntityRenderState state = null;
        try {
            state = original.call(dispatcher, blockEntity, partial, breaking, global, frustum);
            return state;
        } finally {
            long ns = FrameProbe.end(start);
            if (state != null) {
                FrameProbe.blockEntity(blockEntity, ns);       // one it draws (most block entities in view draw nothing)
                FrameProbe.stateOf(state, blockEntity);
            } else {
                FrameProbe.phase(FramePhase.EXTRACT, ns);
            }
        }
    }

    @WrapOperation(require = 0, method = "extract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;extract(Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;F)V"))
    private void culprit$particles(ParticleEngine engine, ParticlesRenderState state, Frustum frustum, Camera camera, float partial,
                                   Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(engine, state, frustum, camera, partial);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(engine, state, frustum, camera, partial);
        } finally {
            FrameProbe.phase(FramePhase.PARTICLES, FrameProbe.end(start));
        }
    }
}
