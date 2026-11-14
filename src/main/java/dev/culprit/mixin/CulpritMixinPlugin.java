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

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !(NO_HOOKS && (mixinClassName.endsWith(".LevelTickMixin") || mixinClassName.endsWith(".ServerLevelTickMixin")));
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
