package dev.culprit.mixin;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distant Horizons' far terrain (DESIGN §6): its render calls on the render thread (the far terrain, the pass a shader
 * pack defers, the fades between it and the game's terrain) timed as their own part of the frame, "Distant Horizons",
 * where they would otherwise count as drawing the terrain. Applies only with Distant Horizons, and leaves the part
 * unmeasured where it cannot. Each method keeps its own start, so one called inside another still adds up.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
abstract class DistantHorizonsFrameMixin {
    @Unique
    private static boolean culprit$lods, culprit$deferred, culprit$fadeOpaque, culprit$fadeTransparent;
    @Unique
    private static long culprit$lodsStart, culprit$deferredStart, culprit$fadeOpaqueStart, culprit$fadeTransparentStart;

    @Inject(require = 0, method = "renderLods", at = @At("HEAD"))
    private void culprit$lodsBegin(CallbackInfo ci) {
        if (culprit$lods = FrameProbe.measuring()) {
            culprit$lodsStart = FrameProbe.begin();
        }
    }

    @Inject(require = 0, method = "renderLods", at = @At("RETURN"))
    private void culprit$lodsEnd(CallbackInfo ci) {
        if (culprit$lods) {
            culprit$lods = false;
            FrameProbe.phase(FramePhase.FAR_TERRAIN, FrameProbe.end(culprit$lodsStart));
        }
    }

    @Inject(require = 0, method = "renderDeferredLodsForShaders", at = @At("HEAD"))
    private void culprit$deferredBegin(CallbackInfo ci) {
        if (culprit$deferred = FrameProbe.measuring()) {
            culprit$deferredStart = FrameProbe.begin();
        }
    }

    @Inject(require = 0, method = "renderDeferredLodsForShaders", at = @At("RETURN"))
    private void culprit$deferredEnd(CallbackInfo ci) {
        if (culprit$deferred) {
            culprit$deferred = false;
            FrameProbe.phase(FramePhase.FAR_TERRAIN, FrameProbe.end(culprit$deferredStart));
        }
    }

    @Inject(require = 0, method = "renderFadeOpaque", at = @At("HEAD"))
    private void culprit$fadeOpaqueBegin(CallbackInfo ci) {
        if (culprit$fadeOpaque = FrameProbe.measuring()) {
            culprit$fadeOpaqueStart = FrameProbe.begin();
        }
    }

    @Inject(require = 0, method = "renderFadeOpaque", at = @At("RETURN"))
    private void culprit$fadeOpaqueEnd(CallbackInfo ci) {
        if (culprit$fadeOpaque) {
            culprit$fadeOpaque = false;
            FrameProbe.phase(FramePhase.FAR_TERRAIN, FrameProbe.end(culprit$fadeOpaqueStart));
        }
    }

    @Inject(require = 0, method = "renderFadeTransparent", at = @At("HEAD"))
    private void culprit$fadeTransparentBegin(CallbackInfo ci) {
        if (culprit$fadeTransparent = FrameProbe.measuring()) {
            culprit$fadeTransparentStart = FrameProbe.begin();
        }
    }

    @Inject(require = 0, method = "renderFadeTransparent", at = @At("RETURN"))
    private void culprit$fadeTransparentEnd(CallbackInfo ci) {
        if (culprit$fadeTransparent) {
            culprit$fadeTransparent = false;
            FrameProbe.phase(FramePhase.FAR_TERRAIN, FrameProbe.end(culprit$fadeTransparentStart));
        }
    }
}
