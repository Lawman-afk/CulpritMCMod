package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris's shadow pass (DESIGN §4.2, §6): a shader pack draws the world again from the sun, gathering and handing in the
 * entities and block entities itself. The pass is its own part of the frame, and each thing's share of it (its shadow's
 * render state and parts) is charged to the thing, as in the main pass; the parts it hands in are drawn and shared out
 * with the rest. Applies only with Iris, and leaves a part unmeasured where it cannot.
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

    @WrapOperation(require = 0, method = {"extractVisibleEntities", "renderShadows"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
    private EntityRenderState culprit$entity(EntityRenderDispatcher dispatcher, Entity entity, float partial, Operation<EntityRenderState> original) {
        if (!FrameProbe.measuring()) {
            return original.call(dispatcher, entity, partial);
        }
        long start = FrameProbe.begin();
        EntityRenderState state = null;
        try {
            state = original.call(dispatcher, entity, partial);
            return state;
        } finally {
            FrameProbe.entity(entity, FrameProbe.end(start));
            FrameProbe.stateOf(state, entity);
        }
    }

    @WrapOperation(require = 0, method = "renderEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void culprit$entitySubmit(EntityRenderDispatcher dispatcher, EntityRenderState state, CameraRenderState camera, double x, double y,
                                      double z, PoseStack pose, SubmitNodeCollector collector, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(dispatcher, state, camera, x, y, z, pose, collector);
            return;
        }
        long start = FrameProbe.begin();
        long before = FrameProbe.parts;
        try {
            original.call(dispatcher, state, camera, x, y, z, pose, collector);
        } finally {
            FrameProbe.drawn(state, FrameProbe.end(start), FrameProbe.parts - before);
        }
    }

    @WrapOperation(require = 0, method = "renderBlockEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void culprit$blockEntitySubmit(BlockEntityRenderDispatcher dispatcher, BlockEntityRenderState state, PoseStack pose,
                                           SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(dispatcher, state, pose, collector, camera);
            return;
        }
        long start = FrameProbe.begin();
        long before = FrameProbe.parts;
        try {
            original.call(dispatcher, state, pose, collector, camera);
        } finally {
            FrameProbe.drawn(state, FrameProbe.end(start), FrameProbe.parts - before);
        }
    }
}
