package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The level's drawing, timed during a frame scan, 1.21.1's way (this branch's own, manual.txt): the world drawn ("drawing
 * the world": the terrain, sky, clouds and weather, what of it no other part times), the light updates the client
 * works out first, the changed chunks' meshes built, the things' buffered parts sent to the GPU as each batch ends
 * ("drawing the buffered parts"; 1.21.1 hands nothing in to count, so that part is not shared out to the things), and the
 * mods' code run at each stage of it (NeoForge's level stage events).
 */
// Above Sodium's 1000: Sodium replaces setupRender and renderSectionLayer (@Overwrite), and a mixin may hook another's
// replacement only from a higher priority (an error at load, even for an optional hook, where it may not).
@Mixin(value = LevelRenderer.class, priority = 1100)
abstract class LevelRendererFrameMixin {
    @WrapMethod(require = 0, method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V")
    private void culprit$world(DeltaTracker delta, boolean outline, Camera camera, GameRenderer game, LightTexture light, Matrix4f modelView, Matrix4f projection, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(delta, outline, camera, game, light, modelView, projection);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(delta, outline, camera, game, light, modelView, projection);
        } finally {
            FrameProbe.phase(FramePhase.DRAWING, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;pollLightUpdates()V"))
    private void culprit$lightQueue(ClientLevel level, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(level);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(level);
        } finally {
            FrameProbe.phase(FramePhase.LIGHT, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runLightUpdates()I"))
    private int culprit$light(LevelLightEngine engine, Operation<Integer> original) {
        if (!FrameProbe.measuring()) {
            return original.call(engine);
        }
        long start = FrameProbe.begin();
        try {
            return original.call(engine);
        } finally {
            FrameProbe.phase(FramePhase.LIGHT, FrameProbe.end(start));
        }
    }

    @WrapMethod(require = 0, method = "compileSections(Lnet/minecraft/client/Camera;)V")
    private void culprit$chunks(Camera camera, Operation<Void> original) {
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

    @WrapOperation(require = 0, method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch(Lnet/minecraft/client/renderer/RenderType;)V"))
    private void culprit$batch(MultiBufferSource.BufferSource buffers, RenderType type, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(buffers, type);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(buffers, type);
        } finally {
            FrameProbe.phase(FramePhase.FEATURES, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endBatch()V"))
    private void culprit$batches(MultiBufferSource.BufferSource buffers, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(buffers);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(buffers);
        } finally {
            FrameProbe.phase(FramePhase.FEATURES, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V"))
    private void culprit$lastBatch(MultiBufferSource.BufferSource buffers, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(buffers);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(buffers);
        } finally {
            FrameProbe.phase(FramePhase.FEATURES, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;dispatchRenderStage(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V"))
    private void culprit$stage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage stage, LevelRenderer renderer, PoseStack pose, Matrix4f modelView, Matrix4f projection, int ticks, Camera camera, Frustum frustum, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(stage, renderer, pose, modelView, projection, ticks, camera, frustum);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(stage, renderer, pose, modelView, projection, ticks, camera, frustum);
        } finally {
            FrameProbe.phase(FramePhase.MOD_EVENTS, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;dispatchRenderStage(Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V"))
    private void culprit$layerStage(RenderType layer, LevelRenderer renderer, Matrix4f modelView, Matrix4f projection, int ticks, Camera camera, Frustum frustum, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(layer, renderer, modelView, projection, ticks, camera, frustum);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(layer, renderer, modelView, projection, ticks, camera, frustum);
        } finally {
            FrameProbe.phase(FramePhase.MOD_EVENTS, FrameProbe.end(start));
        }
    }
}
