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

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;tick()V"))
    private void culprit$timeConnections(net.minecraft.server.network.ServerConnectionListener connections, Operation<Void> original) {
        if (!Probe.measuring()) {
            original.call(connections);
            return;
        }
        long start = Probe.begin();
        try {
            original.call(connections);
        } finally {
            Probe.phase(Phase.CONNECTIONS, Probe.end(start));
        }
    }

    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;saveEverything(ZZZ)Z"))
    private boolean culprit$timeAutosave(MinecraftServer server, boolean quietly, boolean flush, boolean forced, Operation<Boolean> original) {
        if (!Probe.measuring()) {
            return original.call(server, quietly, flush, forced);
        }
        long start = Probe.begin();
        try {
            return original.call(server, quietly, flush, forced);
        } finally {
            Probe.phase(Phase.AUTOSAVE, Probe.end(start));
        }
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPreLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V",
            remap = false))
    private void culprit$timeLevelTickPre(Level level, BooleanSupplier haveTime, Operation<Void> original) {
        culprit$levelEvents(level, haveTime, original);
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;onPostLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V",
            remap = false))
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
