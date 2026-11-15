package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Oculus's shadow pass (Iris for Forge 1.20.1, in Iris's packages; DESIGN §4.2, §6), this branch's own (manual.txt): a
 * shader pack draws the world again from the sun. The pass is its own part of the frame; its entities are drawn through
 * the level renderer's own method, so their shadows are charged to them there (LevelExtractorFrameMixin), and its block
 * entities' shadows here. Applies only with Oculus. Oculus's methods are its own (not remapped); the game's call in them
 * is (remapped for players' games).
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
abstract class IrisShadowFrameMixin {
    @Unique
    private static boolean culprit$timing;
    @Unique
    private static long culprit$start;

    @Inject(require = 0, method = "renderShadows", at = @At("HEAD"))
    private void culprit$shadowsBegin(CallbackInfo ci) {
        culprit$timing = FrameProbe.measuring();
        if (culprit$timing) {
            culprit$start = FrameProbe.begin();
        }
    }

    @Inject(require = 0, method = "renderShadows", at = @At("RETURN"))
    private void culprit$shadowsEnd(CallbackInfo ci) {
        if (culprit$timing) {
            culprit$timing = false;
            FrameProbe.phase(FramePhase.SHADOWS, FrameProbe.end(culprit$start));
        }
    }

    @WrapOperation(require = 0, method = "renderBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V", remap = true))
    private void culprit$blockEntity(BlockEntityRenderDispatcher dispatcher, BlockEntity blockEntity, float partial, PoseStack pose,
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
