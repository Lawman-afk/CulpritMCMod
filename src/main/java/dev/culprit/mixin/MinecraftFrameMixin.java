package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.FrameScan;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The frame loop, timed during a frame scan (DESIGN §4.2), 1.20.1's way (this branch's own, manual.txt; as 1.21.1's):
 * each frame from its start to its end round {@code runTick}, and its parts outside the world's drawing: the client's
 * ticks, the queued tasks and packets, showing the frame (the screen cleared for it, the frame copied to the window and
 * the window's buffers swapped, where the wait for the GPU and the screen falls) and the frame-rate limit's wait (mods'
 * frame events are timed by listeners, client/FrameEvents). While no frame scan is armed, one boolean read a frame.
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

    @WrapOperation(require = 0, method = "runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void culprit$clear(int mask, boolean checkError, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(mask, checkError);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(mask, checkError);
        } finally {
            FrameProbe.phase(FramePhase.PRESENT, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen(II)V"))
    private void culprit$blit(RenderTarget target, int width, int height, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(target, width, height);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(target, width, height);
        } finally {
            FrameProbe.phase(FramePhase.PRESENT, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;updateDisplay()V"))
    private void culprit$present(Window window, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(window);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(window);
        } finally {
            FrameProbe.phase(FramePhase.PRESENT, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "runTick(Z)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS(I)V"))
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
