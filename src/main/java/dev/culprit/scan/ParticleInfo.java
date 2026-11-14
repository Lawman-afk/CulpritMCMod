package dev.culprit.scan;

import org.jspecify.annotations.Nullable;

/**
 * What a frame scan needs to know of a particle (added to every particle by mixin/ParticleMixin): its type (set when
 * the particle engine makes it; null for one added another way, named then by its class) and where it is.
 */
public interface ParticleInfo {
    void culprit$setType(Object type);

    @Nullable Object culprit$type();

    double culprit$x();

    double culprit$z();
}
