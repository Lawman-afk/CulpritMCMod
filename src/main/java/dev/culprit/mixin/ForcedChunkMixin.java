package dev.culprit.mixin;

import dev.culprit.chunks.ForcedBy;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Each change to a chunk's forced ticket (the one /forceload and the game's setChunkForced share), recorded with who
 * made it (DESIGN §4.3). Called only when a chunk is forced or let go, which is rare; not needed to play, so a game
 * where it cannot apply starts without it.
 */
@Mixin(ServerChunkCache.class)
abstract class ForcedChunkMixin {
    @Inject(method = "updateChunkForced", at = @At("RETURN"), require = 0)
    private void culprit$recordForced(ChunkPos pos, boolean forced, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            ForcedBy.changed(((ServerChunkCache) (Object) this).level, pos, forced);
        }
    }
}
