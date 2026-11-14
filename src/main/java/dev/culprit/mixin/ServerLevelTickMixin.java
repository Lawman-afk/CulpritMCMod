package dev.culprit.mixin;

import java.util.function.BooleanSupplier;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.culprit.scan.Phase;
import dev.culprit.scan.Probe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The server level's own ticking, timed during a tick scan (DESIGN §5.1): each entity (a vehicle apart from its
 * passengers), each position's scheduled block and fluid ticks, each block event, and each chunk's own tick (random
 * ticks, weather). The same methods in every version.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelTickMixin {
    private ServerLevel culprit$self() {
        return (ServerLevel) (Object) this;
    }

    @WrapMethod(method = "tickNonPassenger")
    private void culprit$timeEntity(Entity entity, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(entity);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(entity);
        } finally {
            Probe.entity(culprit$self(), entity, Probe.end(start));
        }
    }

    @WrapMethod(method = "tickPassenger")
    private void culprit$timePassenger(Entity vehicle, Entity passenger, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(vehicle, passenger);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(vehicle, passenger);
        } finally {
            Probe.entity(culprit$self(), passenger, Probe.end(start));
        }
    }

    @WrapMethod(method = "tickBlock")
    private void culprit$timeBlockTick(BlockPos pos, Block block, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(pos, block);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(pos, block);
        } finally {
            Probe.blockTick(culprit$self(), pos, block, Probe.end(start));
        }
    }

    @WrapMethod(method = "tickFluid")
    private void culprit$timeFluidTick(BlockPos pos, Fluid fluid, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(pos, fluid);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(pos, fluid);
        } finally {
            Probe.fluidTick(culprit$self(), pos, fluid, Probe.end(start));
        }
    }

    @WrapMethod(method = "doBlockEvent")
    private boolean culprit$timeBlockEvent(BlockEventData event, Operation<Boolean> original) {
        if (!Probe.measuring()) {
            return original.call(event);
        }
        long start = Probe.begin();
        try {
            return original.call(event);
        } finally {
            Probe.blockEvent(culprit$self(), event.pos(), event.block(), Probe.end(start));
        }
    }

    @WrapMethod(method = "tickChunk")
    private void culprit$timeChunk(LevelChunk chunk, int tickSpeed, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(chunk, tickSpeed);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(chunk, tickSpeed);
        } finally {
            Probe.chunk(culprit$self(), chunk, Probe.end(start));
        }
    }

    /** A block's random tick, charged to that kind of block in that chunk (and so to its mod). */
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void culprit$timeRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(state, level, pos, random);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(state, level, pos, random);
        } finally {
            Probe.randomTick(level, pos, state.getBlock(), Probe.end(start));
        }
    }

    /** A fluid's random tick (lava setting fire), charged as a block's is. */
    @WrapOperation(method = "tickChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/material/FluidState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void culprit$timeFluidRandomTick(FluidState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(state, level, pos, random);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(state, level, pos, random);
        } finally {
            Probe.randomTick(level, pos, state.getType(), Probe.end(start));
        }
    }

    /** The level's whole tick: its own upkeep is what the things and phases inside it did not take. */
    @WrapMethod(method = "tick(Ljava/util/function/BooleanSupplier;)V")
    private void culprit$timeLevel(BooleanSupplier haveTime, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(haveTime);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(haveTime);
        } finally {
            Probe.phase(Phase.LEVEL, Probe.end(start));
        }
    }
}
