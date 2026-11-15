package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The drawing of the handed-in parts (DESIGN §5.3), 26.1.2's way (this branch's own, manual.txt): its feature dispatcher
 * draws the solid parts, the translucent ones and the particles straight from what was handed in, with no frame prepared
 * and sorted first as on 26.3. Timed as "drawing the handed-in parts" and shared out to the things by their parts.
 */
@Mixin(FeatureRenderDispatcher.class)
abstract class PreparedFrameMixin {
    @WrapMethod(require = 0, method = {"renderSolidFeatures", "renderTranslucentFeatures", "renderTranslucentParticles"})
    private void culprit$draw(Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call();
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call();
        } finally {
            FrameProbe.phase(FramePhase.FEATURES, FrameProbe.end(start));
        }
    }
}
