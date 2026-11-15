package dev.culprit.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Lets the idle-cost benchmark run without the tick hooks ({@code -Dculprit.noHooks=true}, set only by the dev run
 * {@code -PnoHooks}), to compare the server's tick with and without them. Players' games never set it.
 */
public final class CulpritMixinPlugin implements IMixinConfigPlugin {
    private static final boolean NO_HOOKS = Boolean.getBoolean("culprit.noHooks");
    /**
     * The hooks into other mods (DESIGN §6), each applied only when that mod is installed: without it, the game would
     * still start (they are optional) but log a warning for each class it looked for.
     */
    private static final java.util.Map<String, String> NEEDS = java.util.Map.of("SodiumFrameMixin", "embeddium", "SodiumChunksFrameMixin", "embeddium",
            "IrisShadowFrameMixin", "oculus", "DistantHorizonsFrameMixin", "distanthorizons");

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String needs = NEEDS.get(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
        if (needs != null && !installed(needs)) {
            return false;
        }
        return !(NO_HOOKS && !mixinClassName.endsWith(".DevMouseMixin"));   // every tick hook out, for the comparison
    }

    private static boolean installed(String modId) {
        try {
            var mods = net.minecraftforge.fml.loading.LoadingModList.get();
            return mods == null || mods.getModFileById(modId) != null;
        } catch (RuntimeException | LinkageError e) {
            return true;                                        // (cannot tell: the optional hook looks for itself)
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
