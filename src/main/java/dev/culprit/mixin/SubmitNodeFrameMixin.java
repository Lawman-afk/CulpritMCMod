package dev.culprit.mixin;

import dev.culprit.scan.FrameProbe;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every part handed in to be drawn (a model, an item, a text, a shadow...), counted during a frame scan: what each
 * entity and block entity hands in is its share of the sorting and drawing of them all (DESIGN §5.3).
 */
@Mixin(SubmitNodeCollection.class)
abstract class SubmitNodeFrameMixin {
    @Inject(require = 0, at = @At("HEAD"), method = {"submitShadow", "submitNameTag", "submitText", "submitTextBackground", "submitFlame",
            "submitLeash", "submitModel", "submitCrumblingOverlay", "submitMovingBlock", "submitBlockModel", "submitMultiLayerBlockModel",
            "submitBreakingBlockModel", "submitShapeOutline", "submitItem", "submitCustomGeometry", "submitSpecial"})
    private void culprit$part(CallbackInfo ci) {
        if (FrameProbe.active) {
            FrameProbe.parts++;
        }
    }
}
