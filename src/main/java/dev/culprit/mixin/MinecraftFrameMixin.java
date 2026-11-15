package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;



import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame loop, timed during a frame scan (DESIGN §4.2): each frame from its start to its end, and its parts outside
 * the world's drawing: the client's ticks, the packets and queued tasks, the light updates (the client works out the
 * world's light again where blocks changed, once a frame), showing the frame (waiting for the GPU or the screen) and the
 * frame-rate limit's wait (mods' frame events are timed by listeners, client/FrameEvents). The frame's
 * start and end are plain injections round {@code runTick}; its drawing half is {@code renderFrame} (called from it),
 * where the hand-over to the GPU, the showing and the limit's wait are. While no frame scan is armed, one boolean read a
 * frame.
 */
@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
    @Inject(require = 0, method = "runTick(Z)V", at = @At("HEAD"))
    private void culprit$frameStarts(boolean advanceGameTime, CallbackInfo ci) {
        if (FrameScan.armed) {
            FrameScan.frameStarts();
        }
    }

    @Inject(require = 0, method = "runTick(Z)V", at = @At("RETURN"))
    private void culprit$frameEnds(boolean advanceGameTime, CallbackInfo ci) {
        if (FrameScan.armed) {
            FrameScan.frameEnds();
        }
    }

    @WrapMethod(require = 0, method = "tick()V")
    private void culprit$clientTick(Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call();
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call();
        } finally {
            FrameProbe.phase(FramePhase.CLIENT_TICK, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "runTick(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketProcessor;processQueuedPackets()V"))
    private void culprit$packets(PacketProcessor processor, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(processor);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(processor);
        } finally {
            FrameProbe.phase(FramePhase.PACKETS, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "runTick(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
    private void culprit$tasks(Minecraft self, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(self);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(self);
        } finally {
            FrameProbe.phase(FramePhase.PACKETS, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderFrame(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;update()V"))
    private void culprit$light(ClientLevel level, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(level);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(level);
        } finally {
            FrameProbe.phase(FramePhase.LIGHT, FrameProbe.end(start));
        }
    }

    /**
     * The drawing half of the frame: what of it no other part times is the wait for the GPU and the screen (wherever the
     * driver puts it: fetching the next image, copying into it, showing it, or the first use of a buffer still in use)
     * and the frame's housekeeping, all "showing the frame".
     */
    @WrapMethod(require = 0, method = "renderFrame(Z)V")
    private void culprit$renderFrame(boolean advanceGameTime, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(advanceGameTime);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(advanceGameTime);
        } finally {
            FrameProbe.phase(FramePhase.PRESENT, FrameProbe.end(start));
        }
    }

    // 26.1.2 copies the frame to the window and swaps the window's buffers: where the wait for the screen falls.
    @WrapOperation(require = 0, method = "renderFrame(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V"))
    private void culprit$blitToScreen(com.mojang.blaze3d.pipeline.RenderTarget target, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(target);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(target);
        } finally {
            FrameProbe.phase(FramePhase.PRESENT, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderFrame(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame(Lcom/mojang/blaze3d/TracyFrameCapture;)V"))
    private void culprit$flipFrame(com.mojang.blaze3d.TracyFrameCapture capture, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(capture);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(capture);
        } finally {
            FrameProbe.phase(FramePhase.PRESENT, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderFrame(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/FramerateLimiter;limitDisplayFPS(I)V"))
    private void culprit$limit(int limit, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(limit);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(limit);
        } finally {
            FrameProbe.phase(FramePhase.LIMIT, FrameProbe.end(start));
        }
    }
}
