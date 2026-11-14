package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.ParticleInfo;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A particle made ready to draw (most kinds are quads), timed during a frame scan and charged to its kind in its chunk. */
@Mixin(QuadParticleGroup.class)
abstract class QuadParticleFrameMixin {
    @WrapOperation(require = 0, method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/SingleQuadParticle;extract(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V"))
    private void culprit$extract(SingleQuadParticle particle, QuadParticleRenderState state, Camera camera, float partial, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(particle, state, camera, partial);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(particle, state, camera, partial);
        } finally {
            long ns = FrameProbe.end(start);
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                FrameProbe.particle(level, (ParticleInfo) particle, ns);
            }
        }
    }
}
