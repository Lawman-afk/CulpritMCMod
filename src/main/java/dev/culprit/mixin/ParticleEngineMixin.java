package dev.culprit.mixin;

import dev.culprit.scan.ParticleInfo;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A particle made by the engine is told its type (a field set, always: a frame scan may start while it lives). */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    @Inject(require = 0, method = "createParticle", at = @At("RETURN"))
    private void culprit$type(ParticleOptions options, double x, double y, double z, double xa, double ya, double za,
                              CallbackInfoReturnable<Particle> cir) {
        Particle particle = cir.getReturnValue();
        if (particle != null) {
            ((ParticleInfo) particle).culprit$setType(options.getType());
        }
    }
}
