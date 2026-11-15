package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.culprit.scan.FramePhase;
import dev.culprit.scan.FrameProbe;
import net.minecraft.client.Camera;
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
 * The game renderer's parts, timed during a frame scan, 1.20.1's way (this branch's own, manual.txt): the HUD, the open
 * screen and the toasts drawn, and their batched parts sent to the GPU; the world's drawing ("drawing the world": what
 * of it no other part times, your hand among it), and the mods' code run when it is done.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererFrameMixin {
    @WrapOperation(require = 0, method = "render(FJZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V"))
    private void culprit$hud(Gui gui, GuiGraphics graphics, float partial, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(gui, graphics, partial);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(gui, graphics, partial);
        } finally {
            FrameProbe.phase(FramePhase.GUI, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "render(FJZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V", remap = false))
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

    @WrapOperation(require = 0, method = "render(FJZ)V",
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

    @WrapOperation(require = 0, method = "render(FJZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V"))
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

    @WrapMethod(require = 0, method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V")
    private void culprit$world(float partial, long nanos, PoseStack pose, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(partial, nanos, pose);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(partial, nanos, pose);
        } finally {
            FrameProbe.phase(FramePhase.DRAWING, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;dispatchRenderStage(Lnet/minecraftforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V", remap = false))
    private void culprit$worldDone(net.minecraftforge.client.event.RenderLevelStageEvent.Stage stage, LevelRenderer renderer, PoseStack pose, Matrix4f projection, int ticks, Camera camera, Frustum frustum, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(stage, renderer, pose, projection, ticks, camera, frustum);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(stage, renderer, pose, projection, ticks, camera, frustum);
        } finally {
            FrameProbe.phase(FramePhase.MOD_EVENTS, FrameProbe.end(start));
        }
    }
}
