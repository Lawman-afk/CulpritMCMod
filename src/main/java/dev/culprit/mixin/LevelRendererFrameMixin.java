package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.FramePhase;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The second step of drawing the world, timed during a frame scan: each entity's and block entity's parts handed in
 * (charged to the thing its state was made from), the particles', the rest of the handing in, the sorting, the drawing
 * itself (terrain and the sorted parts sent to the GPU) and the chunk meshes built and uploaded.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererFrameMixin {
    @WrapMethod(require = 0, method = "submitFeatures")
    private void culprit$submit(LevelRenderState state, SubmitNodeCollector collector, boolean outline, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(state, collector, outline);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(state, collector, outline);
        } finally {
            FrameProbe.phase(FramePhase.SUBMIT, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "submitEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void culprit$entity(EntityRenderDispatcher dispatcher, EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                                PoseStack pose, SubmitNodeCollector collector, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(dispatcher, state, camera, x, y, z, pose, collector);
            return;
        }
        long start = FrameProbe.begin();
        long before = FrameProbe.parts;
        try {
            original.call(dispatcher, state, camera, x, y, z, pose, collector);
        } finally {
            FrameProbe.drawn(state, FrameProbe.end(start), FrameProbe.parts - before);
        }
    }

    @WrapOperation(require = 0, method = "submitBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void culprit$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                     CameraRenderState camera, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(dispatcher, state, pose, collector, camera);
            return;
        }
        long start = FrameProbe.begin();
        long before = FrameProbe.parts;
        try {
            original.call(dispatcher, state, pose, collector, camera);
        } finally {
            FrameProbe.drawn(state, FrameProbe.end(start), FrameProbe.parts - before);
        }
    }

    @WrapOperation(require = 0, method = "submitFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/level/ParticlesRenderState;submit(Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void culprit$particles(ParticlesRenderState state, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(state, collector, camera);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(state, collector, camera);
        } finally {
            FrameProbe.phase(FramePhase.PARTICLES, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;"))
    private FeatureRenderDispatcher.PreparedFrame culprit$sort(FeatureRenderDispatcher dispatcher, SubmitNodeStorage storage,
                                                               Operation<FeatureRenderDispatcher.PreparedFrame> original) {
        if (!FrameProbe.measuring()) {
            return original.call(dispatcher, storage);
        }
        long start = FrameProbe.begin();
        try {
            return original.call(dispatcher, storage);
        } finally {
            FrameProbe.phase(FramePhase.BATCHING, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V"))
    private void culprit$draw(FrameGraphBuilder frame, GraphicsResourceAllocator allocator, FrameGraphBuilder.Inspector inspector, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(frame, allocator, inspector);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(frame, allocator, inspector);
        } finally {
            FrameProbe.phase(FramePhase.DRAWING, FrameProbe.end(start));
        }
    }

    @WrapMethod(require = 0, method = "compileSections")
    private void culprit$chunks(CameraRenderState camera, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(camera);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(camera);
        } finally {
            FrameProbe.phase(FramePhase.CHUNKS, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V"))
    private void culprit$upload(SectionRenderDispatcher dispatcher, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(dispatcher);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(dispatcher);
        } finally {
            FrameProbe.phase(FramePhase.CHUNKS, FrameProbe.end(start));
        }
    }
}
