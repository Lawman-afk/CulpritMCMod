package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The level's things drawn, each its own cost, 1.20.1's way (this branch's own, manual.txt; as 1.21.1's): 1.20.1 draws
 * each entity and block entity as it goes, so each one's whole drawing is timed and charged to it; Oculus's shadow pass
 * draws its entities through the same method, so their shadows are theirs too. Finding what is in view
 * ({@code setupRender}) and the particles are parts of the frame.
 */
// Above Embeddium's 1000: Embeddium replaces setupRender (@Overwrite), and a mixin may hook another's replacement only
// from a higher priority (an error at load, even for an optional hook, where it may not).
@Mixin(value = LevelRenderer.class, priority = 1100)
abstract class LevelExtractorFrameMixin {
    @WrapMethod(require = 0, method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V")
    private void culprit$entity(Entity entity, double x, double y, double z, float partial, PoseStack pose, MultiBufferSource buffers, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(entity, x, y, z, partial, pose, buffers);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(entity, x, y, z, partial, pose, buffers);
        } finally {
            FrameProbe.entity(entity, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void culprit$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float partial, PoseStack pose, MultiBufferSource buffers,
                                     Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(dispatcher, blockEntity, partial, pose, buffers);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(dispatcher, blockEntity, partial, pose, buffers);
        } finally {
            FrameProbe.blockEntity(blockEntity, FrameProbe.end(start));
        }
    }

    @WrapMethod(require = 0, method = "setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V")
    private void culprit$inView(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean spectator, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(camera, frustum, hasCapturedFrustum, spectator);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(camera, frustum, hasCapturedFrustum, spectator);
        } finally {
            FrameProbe.phase(FramePhase.EXTRACT, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V", remap = false))
    private void culprit$particles(ParticleEngine engine, PoseStack pose, MultiBufferSource.BufferSource buffers, LightTexture light, Camera camera, float partial, Frustum frustum, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(engine, pose, buffers, light, camera, partial, frustum);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(engine, pose, buffers, light, camera, partial, frustum);
        } finally {
            FrameProbe.phase(FramePhase.PARTICLES, FrameProbe.end(start));
        }
    }
}
