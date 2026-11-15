package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.ParticleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;

/** A particle moved (its tick), timed during a frame scan and charged to its kind in its chunk. */
@Mixin(ParticleEngine.class)
abstract class ParticleFrameMixin {
    @WrapMethod(require = 0, method = "tickParticle(Lnet/minecraft/client/particle/Particle;)V")
    private void culprit$tick(Particle particle, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(particle);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(particle);
        } finally {
            long ns = FrameProbe.end(start);
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                FrameProbe.particle(level, (ParticleInfo) particle, ns);
            }
        }
    }
}
