package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.FramePhase;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The game renderer's parts, timed during a frame scan: finding what of the world is in view (the entities and block
 * entities inside it charged apart), and the HUD and screens, gathered and drawn.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererFrameMixin {
    @WrapOperation(require = 0, method = "extract(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extract(Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/Camera;F)V"))
    private void culprit$extractLevel(LevelExtractor extractor, DeltaTracker delta, Camera camera, float partial, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(extractor, delta, camera, partial);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(extractor, delta, camera, partial);
        } finally {
            FrameProbe.phase(FramePhase.EXTRACT, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "extract(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V"))
    private void culprit$extractGui(Gui gui, DeltaTracker delta, boolean level, boolean loaded, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(gui, delta, level, loaded);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(gui, delta, level, loaded);
        } finally {
            FrameProbe.phase(FramePhase.GUI, FrameProbe.end(start));
        }
    }

    @WrapOperation(require = 0, method = "render()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"))
    private void culprit$renderGui(GuiRenderer renderer, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(renderer);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(renderer);
        } finally {
            FrameProbe.phase(FramePhase.GUI, FrameProbe.end(start));
        }
    }
}
