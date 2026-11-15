package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The game renderer's parts, timed during a frame scan, 1.21.1's way (this branch's own, manual.txt): the HUD, the open
 * screen and the toasts drawn, and their batched parts sent to the GPU; the world's drawing ("drawing the world": what
 * of it no other part times, your hand among it), and the mods' code run when it is done.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererFrameMixin {
    @WrapOperation(require = 0, method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void culprit$hud(Gui gui, GuiGraphics graphics, DeltaTracker delta, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(gui, graphics, delta);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(gui, graphics, delta);
        } finally {
            FrameProbe.phase(FramePhase.GUI, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void culprit$screen(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partial, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(screen, graphics, mouseX, mouseY, partial);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(screen, graphics, mouseX, mouseY, partial);
        } finally {
            FrameProbe.phase(FramePhase.GUI, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void culprit$toasts(ToastComponent toasts, GuiGraphics graphics, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(toasts, graphics);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(toasts, graphics);
        } finally {
            FrameProbe.phase(FramePhase.GUI, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
    private void culprit$guiBatch(GuiGraphics graphics, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(graphics);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(graphics);
        } finally {
            FrameProbe.phase(FramePhase.GUI, FrameProbe.end(start));
        }
    }

    @WrapMethod(require = 0, method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V")
    private void culprit$world(DeltaTracker delta, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(delta);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(delta);
        } finally {
            FrameProbe.phase(FramePhase.DRAWING, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;dispatchRenderStage(Lnet/neoforged/neoforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V"))
    private void culprit$worldDone(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage stage, LevelRenderer renderer, PoseStack pose, Matrix4f modelView, Matrix4f projection, int ticks, Camera camera, Frustum frustum, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(stage, renderer, pose, modelView, projection, ticks, camera, frustum);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(stage, renderer, pose, modelView, projection, ticks, camera, frustum);
        } finally {
            FrameProbe.phase(FramePhase.MOD_EVENTS, FrameProbe.end(start));
        }
    }
}
