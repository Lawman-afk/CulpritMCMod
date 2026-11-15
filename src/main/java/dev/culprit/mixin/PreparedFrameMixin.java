package dev.culprit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;

import dev.culprit.scan.FrameProbe;
import dev.culprit.scan.FramePhase;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The drawing of the handed-in parts (each layer of them), timed during a frame scan apart from the terrain's, so it
 * can be shared out to the things that handed them in.
 */
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
abstract class PreparedFrameMixin {
    @WrapMethod(require = 0, method = {"executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V", "executeTranslucent(Lcom/mojang/renderpearl/api/commands/RenderPass;)V", "executeWaterMask(Lcom/mojang/renderpearl/api/commands/RenderPass;)V", "executeOutline(Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            "executeTranslucentAfterTerrain(Lcom/mojang/renderpearl/api/commands/RenderPass;)V", "executeSeeThrough(Lcom/mojang/renderpearl/api/commands/RenderPass;)V", "executeAlwaysOnTop(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"})
    private void culprit$draw(RenderPass pass, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(pass);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(pass);
        } finally {
            FrameProbe.phase(FramePhase.FEATURES, FrameProbe.end(start));
        }
    }

    @WrapMethod(require = 0, method = "executeOit(Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/commands/RenderPass;)V")
    private void culprit$drawOit(OitStage stage, RenderPass pass, Operation<Void> original) {
        if (!FrameProbe.measuring()) {
            original.call(stage, pass);
            return;
        }
        long start = FrameProbe.begin();
        try {
            original.call(stage, pass);
        } finally {
            FrameProbe.phase(FramePhase.FEATURES, FrameProbe.end(start));
        }
    }
}
