package dev.culprit.port.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;

/**
 * Main's GameTests count on every loaded chunk ticking (random ticks, the chunk's own tick), as 26.x's chunks do; 1.21.1
 * ticks a chunk only within 128 blocks of a player (or held by a ticking chunk loader), and the test server has no
 * players, so its plots never ticked. On the test server only, its chunks tick as 26.x's do (this branch's own;
 * docs/BACKPORT.md). Dev runs only: {@link PortMixinPlugin} leaves it out of players' games.
 */
@Mixin(ServerChunkCache.class)
public abstract class GameTestChunkTicksMixin {
    @WrapOperation(method = "tickChunks()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;anyPlayerCloseEnoughForSpawning(Lnet/minecraft/world/level/ChunkPos;)Z"))
    private boolean culprit$testChunksTick(ChunkMap map, ChunkPos pos, Operation<Boolean> original) {
        return original.call(map, pos) || ((ServerChunkCache) (Object) this).getLevel().getServer() instanceof GameTestServer;
    }
}
