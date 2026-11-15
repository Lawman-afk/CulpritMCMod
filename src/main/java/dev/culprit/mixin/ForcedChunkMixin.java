package dev.culprit.mixin;

import dev.culprit.chunks.ForcedBy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Each change to a chunk's forced ticket (the one /forceload and the game's setChunkForced share), recorded with who
 * made it (DESIGN §4.3): the level's own call, which says whether anything changed on every version the mod is made
 * for. Called only when a chunk is forced or let go, which is rare; not needed to play, so a game where it cannot apply
 * starts without it.
 */
@Mixin(ServerLevel.class)
abstract class ForcedChunkMixin {
    @Inject(method = "setChunkForced(IIZ)Z", at = @At("RETURN"), require = 0)
    private void culprit$recordForced(int x, int z, boolean forced, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            ForcedBy.changed((ServerLevel) (Object) this, new ChunkPos(x, z), forced);
        }
    }
}
