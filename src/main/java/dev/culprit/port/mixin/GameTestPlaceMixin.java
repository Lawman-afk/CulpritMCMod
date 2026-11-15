package dev.culprit.port.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;

/**
 * 1.20.1's test server sets its tests out at the world's origin, inside the spawn chunks it always keeps loaded, so a test
 * of what keeps a chunk loaded found the spawn chunks there before the test's own loaders; 1.21.1 sets them out far from
 * spawn. Here they are set out 100 000 blocks off, as far from the spawn chunks as 1.21.1's (this branch's own, not the
 * 1.21.1 branch's; docs/BACKPORT.md). Dev runs only ({@link PortMixinPlugin}).
 */
@Mixin(GameTestServer.class)
public abstract class GameTestPlaceMixin {
    @ModifyArg(method = "startTests", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/gametest/framework/GameTestRunner;runTestBatches(Ljava/util/Collection;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/gametest/framework/GameTestTicker;I)Ljava/util/Collection;"),
            index = 1)
    private BlockPos culprit$farFromSpawn(BlockPos origin) {
        return origin.offset(100_000, 0, 100_000);
    }
}
