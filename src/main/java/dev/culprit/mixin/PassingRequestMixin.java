package dev.culprit.mixin;

import java.util.concurrent.CompletableFuture;

import dev.culprit.chunks.PassingRequests;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A request for a chunk that has to load it (the game runs its chunk updates there and then, only when the chunk is not
 * yet at the asked state): who asked, when it is asked for in full (DESIGN §4.3). Requests for chunks already loaded
 * never reach here. Not needed to play, so a game where it cannot apply starts without it.
 */
@Mixin(ServerChunkCache.class)
abstract class PassingRequestMixin {
    @Inject(method = "getChunkFutureMainThread", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;runDistanceManagerUpdates()Z"))
    private void culprit$whoAsks(int x, int z, ChunkStatus status, boolean load, CallbackInfoReturnable<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> cir) {
        if (status == ChunkStatus.FULL) {
            PassingRequests.loading(((ServerChunkCache) (Object) this).level, ChunkPos.asLong(x, z));
        }
    }
}
