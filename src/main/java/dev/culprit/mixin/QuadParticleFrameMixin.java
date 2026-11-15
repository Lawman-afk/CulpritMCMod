package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.ParticleInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Each particle's drawing, 1.21.1's way (this branch's own, manual.txt): 1.21.1's particles write their own quads as
 * the engine draws them, so each one's drawing is charged to its kind in its chunk, as its move is.
 */
@Mixin(ParticleEngine.class)
abstract class QuadParticleFrameMixin {
    @WrapOperation(require = 0, method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void culprit$draw(Particle particle, VertexConsumer consumer, Camera camera, float partial, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(particle, consumer, camera, partial);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(particle, consumer, camera, partial);
        } finally {
            long ns = FrameProbe.end(start);
            var level = Minecraft.getInstance().level;
            if (level != null) {
                FrameProbe.particle(level, (ParticleInfo) particle, ns);
            } else {
                FrameProbe.phase(FramePhase.PARTICLES, ns);
            }
        }
    }
}
