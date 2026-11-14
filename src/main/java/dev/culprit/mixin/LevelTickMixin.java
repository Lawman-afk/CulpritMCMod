package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.culprit.scan.Probe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Block entity ticks: {@code Level.tickBlockEntities} calls each ticker's {@code tick()} (the same loop in every
 * version); during a tick scan each call on the server is timed and charged to its ticker (DESIGN §5.1).
 */
@Mixin(Level.class)
abstract class LevelTickMixin {
    @WrapOperation(method = "tickBlockEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void culprit$timeBlockEntity(TickingBlockEntity ticker, Operation<Void> original) {
        if (!Probe.measuring() || !((Object) this instanceof ServerLevel level)) {
            original.call(ticker);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(ticker);
        } finally {
            Probe.blockEntity(level, ticker, Probe.end(start));
        }
    }
}
