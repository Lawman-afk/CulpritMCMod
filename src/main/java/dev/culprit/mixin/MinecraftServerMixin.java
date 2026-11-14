package dev.culprit.mixin;

import java.util.function.BooleanSupplier;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.culprit.scan.Phase;
import dev.culprit.scan.Probe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The server's own parts of the tick, timed during a tick scan (DESIGN §4.1): its work round the levels (functions,
 * clocks, the player list; the levels inside are charged apart), the players' connections, other mods' level tick
 * events, and the autosave.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @WrapMethod(method = "tickChildren")
    private void culprit$timeChildren(BooleanSupplier haveTime, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(haveTime);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(haveTime);
        } finally {
            Probe.phase(Phase.SERVER, Probe.end(start));
        }
    }

    @WrapMethod(method = "tickConnection")
    private void culprit$timeConnections(Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call();
            return;
        }
        long start = Probe.begin();
        try {
            original.call();
        } finally {
            Probe.phase(Phase.CONNECTIONS, Probe.end(start));
        }
    }

    @WrapMethod(method = "autoSave")
    private void culprit$timeAutosave(Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call();
            return;
        }
        long start = Probe.begin();
        try {
            original.call();
        } finally {
            Probe.phase(Phase.AUTOSAVE, Probe.end(start));
        }
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/event/EventHooks;fireLevelTickPre(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"))
    private void culprit$timeLevelTickPre(Level level, BooleanSupplier haveTime, Operation<Void> original) {
        culprit$levelEvents(level, haveTime, original);
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/event/EventHooks;fireLevelTickPost(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"))
    private void culprit$timeLevelTickPost(Level level, BooleanSupplier haveTime, Operation<Void> original) {
        culprit$levelEvents(level, haveTime, original);
    }

    private static void culprit$levelEvents(Level level, BooleanSupplier haveTime, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(level, haveTime);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(level, haveTime);
        } finally {
            Probe.phase(Phase.LEVEL_EVENTS, Probe.end(start));
        }
    }
}
