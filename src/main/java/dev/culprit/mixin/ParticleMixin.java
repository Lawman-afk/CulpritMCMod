package dev.culprit.mixin;

import org.jspecify.annotations.Nullable;

import dev.culprit.scan.ParticleInfo;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Every particle keeps its type (one field, set as it is made) and tells where it is, for a frame scan. */
@Mixin(Particle.class)
abstract class ParticleMixin implements ParticleInfo {
    @Shadow
    protected double x;
    @Shadow
    protected double z;
    @Unique
    private @Nullable Object culprit$type;

    @Override
    public void culprit$setType(Object type) {
        this.culprit$type = type;
    }

    @Override
    public @Nullable Object culprit$type() {
        return culprit$type;
    }

    @Override
    public double culprit$x() {
        return x;
    }

    @Override
    public double culprit$z() {
        return z;
    }
}
