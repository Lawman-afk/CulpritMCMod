package dev.culprit.mixin;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.culprit.scan.Phase;
import dev.culprit.scan.Probe;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A level's chunk work, timed during a tick scan (DESIGN §4.1): the whole of it as a phase (loading, saving and
 * sending chunks, thunder; the chunk ticks and spawning inside it are charged apart), and each chunk's mob spawning.
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
    @WrapMethod(method = "tick(Ljava/util/function/BooleanSupplier;Z)V")
    private void culprit$timeChunkSource(BooleanSupplier haveTime, boolean tickChunks, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(haveTime, tickChunks);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(haveTime, tickChunks);
        } finally {
            Probe.phase(Phase.CHUNK_SOURCE, Probe.end(start));
        }
    }

    @WrapOperation(method = "tickSpawningChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;Ljava/util/List;)V"))
    private void culprit$timeSpawning(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, List<MobCategory> categories,
                                      Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(level, chunk, state, categories);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(level, chunk, state, categories);
        } finally {
            Probe.spawning(level, chunk, Probe.end(start));
        }
    }
}
