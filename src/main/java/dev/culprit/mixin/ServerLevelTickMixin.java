package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.culprit.scan.Probe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;

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
}
