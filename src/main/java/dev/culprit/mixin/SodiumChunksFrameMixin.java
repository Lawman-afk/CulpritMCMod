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
 * Sodium's chunk meshes, the render thread's share (DESIGN §6): scheduling the sections to rebuild (and waiting for those
 * needed at once) and taking in the finished ones, Sodium's own way in place of the game's. Timed as "building chunk
 * meshes", as the game's are. Applies only with Sodium, and leaves that part unmeasured where it cannot.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
abstract class SodiumChunksFrameMixin {
    @Unique
    private static boolean culprit$updating, culprit$taking;
    @Unique
    private static long culprit$updateStart, culprit$takeStart;

    @Inject(require = 0, method = "updateChunks", at = @At("HEAD"))
    private void culprit$updateBegin(CallbackInfo ci) {
        culprit$updating = FrameProbe.measuring();
        if (culprit$updating) {
            culprit$updateStart = FrameProbe.begin();
        }
    }

    @Inject(require = 0, method = "updateChunks", at = @At("RETURN"))
    private void culprit$updateEnd(CallbackInfo ci) {
        if (culprit$updating) {
            culprit$updating = false;
            FrameProbe.phase(FramePhase.CHUNKS, FrameProbe.end(culprit$updateStart));
        }
    }

    @Inject(require = 0, method = "uploadChunks", at = @At("HEAD"))
    private void culprit$takeBegin(CallbackInfo ci) {
        culprit$taking = FrameProbe.measuring();
        if (culprit$taking) {
            culprit$takeStart = FrameProbe.begin();
        }
    }

    @Inject(require = 0, method = "uploadChunks", at = @At("RETURN"))
    private void culprit$takeEnd(CallbackInfo ci) {
        if (culprit$taking) {
            culprit$taking = false;
            FrameProbe.phase(FramePhase.CHUNKS, FrameProbe.end(culprit$takeStart));
        }
    }
}
