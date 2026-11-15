package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.culprit.scan.FrameProbe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Embeddium's own drawing of the block entities in view (Sodium for Forge 1.20.1, in Sodium's packages: it replaces the
 * game's loop), this branch's own (manual.txt): each one's drawing timed and charged to it, as the game's is. Applies only
 * with Embeddium, and leaves that part unmeasured where it cannot. Embeddium's method is its own (not remapped); the
 * game's call in it is (remapped for players' games, where it has Forge's names).
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
abstract class SodiumFrameMixin {
    @WrapOperation(require = 0, method = "renderBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", remap = true))
    private static void culprit$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float partial, PoseStack pose,
                                            MultiBufferSource buffers, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(dispatcher, blockEntity, partial, pose, buffers);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(dispatcher, blockEntity, partial, pose, buffers);
        } finally {
            FrameProbe.blockEntity(blockEntity, FrameProbe.end(start));
        }
    }
}
